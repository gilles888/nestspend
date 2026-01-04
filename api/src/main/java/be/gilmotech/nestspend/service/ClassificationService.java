package be.gilmotech.nestspend.service;

import be.gilmotech.nestspend.domain.entity.Category;
import be.gilmotech.nestspend.domain.entity.ClassificationRule;
import be.gilmotech.nestspend.domain.enums.MatchType;
import be.gilmotech.nestspend.domain.enums.RuleField;
import be.gilmotech.nestspend.domain.enums.RuleSource;
import be.gilmotech.nestspend.domain.repository.CategoryRepository;
import be.gilmotech.nestspend.domain.repository.ClassificationRuleRepository;
import be.gilmotech.nestspend.domain.repository.HouseholdRepository;
import be.gilmotech.nestspend.dto.classification.*;
import be.gilmotech.nestspend.exception.ResourceNotFoundException;
import be.gilmotech.nestspend.security.CurrentUserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Service for classification rules and auto-categorization suggestions.
 */
@Service
public class ClassificationService {

    private final ClassificationRuleRepository ruleRepository;
    private final CategoryRepository categoryRepository;
    private final HouseholdRepository householdRepository;
    private final CurrentUserService currentUserService;

    // Base scores for different match types
    private static final int REGEX_BASE_SCORE = 80;
    private static final int STARTS_WITH_BASE_SCORE = 70;
    private static final int CONTAINS_BASE_SCORE = 60;

    // Bonus scores
    private static final int EXACT_MATCH_BONUS = 15;
    private static final int PATTERN_LENGTH_BONUS_THRESHOLD = 5;
    private static final int PATTERN_LENGTH_BONUS = 5;

    public ClassificationService(ClassificationRuleRepository ruleRepository,
                                  CategoryRepository categoryRepository,
                                  HouseholdRepository householdRepository,
                                  CurrentUserService currentUserService) {
        this.ruleRepository = ruleRepository;
        this.categoryRepository = categoryRepository;
        this.householdRepository = householdRepository;
        this.currentUserService = currentUserService;
    }

    // ==================== CRUD Operations ====================

    /**
     * Get all classification rules for the current user's household.
     */
    @Transactional(readOnly = true)
    public List<ClassificationRuleResponse> getAllRules() {
        UUID householdId = currentUserService.getCurrentHouseholdId();
        return ruleRepository.findByHouseholdId(householdId).stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Get a classification rule by ID.
     */
    @Transactional(readOnly = true)
    public ClassificationRuleResponse getRuleById(UUID id) {
        UUID householdId = currentUserService.getCurrentHouseholdId();
        ClassificationRule rule = ruleRepository.findByIdAndHouseholdId(id, householdId)
                .orElseThrow(() -> new ResourceNotFoundException("Classification rule not found"));
        return toResponse(rule);
    }

    /**
     * Create a new classification rule.
     */
    @Transactional
    public ClassificationRuleResponse createRule(ClassificationRuleCreateRequest request) {
        UUID householdId = currentUserService.getCurrentHouseholdId();

        // Validate category belongs to household
        Category category = categoryRepository.findByIdAndHouseholdId(request.categoryId(), householdId)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));

        // Validate regex pattern if applicable
        if (request.matchType() == MatchType.REGEX) {
            validateRegexPattern(request.pattern());
        }

        ClassificationRule rule = ClassificationRule.builder()
                .household(householdRepository.getReferenceById(householdId))
                .field(request.field())
                .matchType(request.matchType())
                .pattern(request.pattern())
                .category(category)
                .enabled(request.enabled() != null ? request.enabled() : true)
                .priority(request.priority() != null ? request.priority() : 0)
                .confidence(request.confidence() != null ? request.confidence() : 80)
                .source(RuleSource.USER)
                .build();

        rule = ruleRepository.save(rule);
        return toResponse(rule);
    }

    /**
     * Update an existing classification rule.
     */
    @Transactional
    public ClassificationRuleResponse updateRule(UUID id, ClassificationRuleUpdateRequest request) {
        UUID householdId = currentUserService.getCurrentHouseholdId();

        ClassificationRule rule = ruleRepository.findByIdAndHouseholdId(id, householdId)
                .orElseThrow(() -> new ResourceNotFoundException("Classification rule not found"));

        // Validate category belongs to household
        Category category = categoryRepository.findByIdAndHouseholdId(request.categoryId(), householdId)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));

        // Validate regex pattern if applicable
        if (request.matchType() == MatchType.REGEX) {
            validateRegexPattern(request.pattern());
        }

        rule.setField(request.field());
        rule.setMatchType(request.matchType());
        rule.setPattern(request.pattern());
        rule.setCategory(category);
        rule.setEnabled(request.enabled());
        rule.setPriority(request.priority());
        rule.setConfidence(request.confidence());

        rule = ruleRepository.save(rule);
        return toResponse(rule);
    }

    /**
     * Delete a classification rule.
     */
    @Transactional
    public void deleteRule(UUID id) {
        UUID householdId = currentUserService.getCurrentHouseholdId();

        ClassificationRule rule = ruleRepository.findByIdAndHouseholdId(id, householdId)
                .orElseThrow(() -> new ResourceNotFoundException("Classification rule not found"));

        ruleRepository.delete(rule);
    }

    // ==================== Suggestion Algorithm ====================

    /**
     * Get classification suggestions for a list of transactions.
     */
    @Transactional(readOnly = true)
    public ClassificationSuggestResponse suggest(ClassificationSuggestRequest request) {
        UUID householdId = currentUserService.getCurrentHouseholdId();

        // Get enabled rules sorted by priority
        List<ClassificationRule> rules = ruleRepository
                .findByHouseholdIdAndEnabledTrueOrderByPriorityDesc(householdId);

        List<ClassificationSuggestion> suggestions = new ArrayList<>();

        for (TransactionToClassify transaction : request.transactions()) {
            ClassificationSuggestion suggestion = suggestForTransaction(transaction, rules);
            suggestions.add(suggestion);
        }

        return new ClassificationSuggestResponse(suggestions);
    }

    /**
     * Suggest a category for a single transaction based on rules.
     */
    private ClassificationSuggestion suggestForTransaction(
            TransactionToClassify transaction,
            List<ClassificationRule> rules) {

        ClassificationSuggestion bestMatch = null;
        int bestScore = 0;

        for (ClassificationRule rule : rules) {
            String fieldValue = getFieldValue(transaction, rule.getField());
            if (fieldValue == null || fieldValue.isEmpty()) {
                continue;
            }

            // Normalize the field value
            String normalizedValue = normalize(fieldValue);
            // Don't normalize regex patterns as it would break the regex syntax
            String patternToMatch = rule.getMatchType() == MatchType.REGEX 
                    ? rule.getPattern() 
                    : normalize(rule.getPattern());

            if (matches(normalizedValue, patternToMatch, rule.getMatchType())) {
                int score = calculateScore(normalizedValue, patternToMatch, rule);

                if (score > bestScore) {
                    bestScore = score;
                    bestMatch = new ClassificationSuggestion(
                            rule.getCategory().getId(),
                            score,
                            ClassificationSuggestion.labelFromScore(score),
                            rule.getId()
                    );
                }
            }
        }

        return bestMatch != null ? bestMatch : ClassificationSuggestion.noMatch();
    }

    /**
     * Normalize a string for matching:
     * - uppercase
     * - trim
     * - remove accents
     * - remove non-alphanumeric characters except spaces
     */
    private String normalize(String value) {
        if (value == null) {
            return "";
        }

        // Uppercase and trim
        String result = value.toUpperCase().trim();

        // Remove accents
        result = Normalizer.normalize(result, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");

        // Remove special characters (keep alphanumeric and spaces)
        result = result.replaceAll("[^A-Z0-9 ]", "");

        return result;
    }

    /**
     * Get the value of the specified field from a transaction.
     */
    private String getFieldValue(TransactionToClassify transaction, RuleField field) {
        return switch (field) {
            case MERCHANT -> transaction.merchant();
            case COMMUNICATION -> transaction.communication();
            case IBAN -> transaction.iban();
        };
    }

    /**
     * Check if a value matches a pattern based on match type.
     */
    private boolean matches(String value, String pattern, MatchType matchType) {
        if (value == null || pattern == null) {
            return false;
        }

        return switch (matchType) {
            case CONTAINS -> value.contains(pattern);
            case STARTS_WITH -> value.startsWith(pattern);
            case REGEX -> {
                try {
                    yield Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).matcher(value).find();
                } catch (PatternSyntaxException e) {
                    yield false;
                }
            }
        };
    }

    /**
     * Calculate the confidence score for a match.
     */
    private int calculateScore(String value, String pattern, ClassificationRule rule) {
        // Base score depends on match type
        int baseScore = switch (rule.getMatchType()) {
            case REGEX -> REGEX_BASE_SCORE;
            case STARTS_WITH -> STARTS_WITH_BASE_SCORE;
            case CONTAINS -> CONTAINS_BASE_SCORE;
        };

        // Apply rule's configured confidence as a modifier
        // The rule confidence modifies the base score
        int score = (baseScore * rule.getConfidence()) / 100;

        // Bonus for exact match
        if (value.equals(pattern)) {
            score += EXACT_MATCH_BONUS;
        }

        // Bonus for longer patterns (more specific)
        if (pattern.length() > PATTERN_LENGTH_BONUS_THRESHOLD) {
            score += PATTERN_LENGTH_BONUS;
        }

        // Cap at 100
        return Math.min(100, score);
    }

    /**
     * Validate a regex pattern.
     */
    private void validateRegexPattern(String pattern) {
        try {
            Pattern.compile(pattern);
        } catch (PatternSyntaxException e) {
            throw new IllegalArgumentException("Invalid regex pattern: " + e.getMessage());
        }
    }

    // ==================== DTO Mapping ====================

    private ClassificationRuleResponse toResponse(ClassificationRule rule) {
        return new ClassificationRuleResponse(
                rule.getId(),
                rule.getField(),
                rule.getMatchType(),
                rule.getPattern(),
                rule.getCategory().getId(),
                rule.getCategory().getName(),
                rule.getEnabled(),
                rule.getPriority(),
                rule.getConfidence(),
                rule.getSource(),
                rule.getCreatedAt(),
                rule.getUpdatedAt()
        );
    }
}
