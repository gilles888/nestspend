package be.gilmotech.nestspend.service;

import be.gilmotech.nestspend.domain.entity.Category;
import be.gilmotech.nestspend.domain.entity.ClassificationRule;
import be.gilmotech.nestspend.domain.enums.MatchType;
import be.gilmotech.nestspend.domain.enums.RuleField;
import be.gilmotech.nestspend.domain.enums.RuleSource;
import be.gilmotech.nestspend.domain.repository.CategoryRepository;
import be.gilmotech.nestspend.domain.repository.ClassificationRuleRepository;
import be.gilmotech.nestspend.domain.repository.HouseholdRepository;
import be.gilmotech.nestspend.domain.repository.TransactionRepository;
import be.gilmotech.nestspend.dto.classification.*;
import be.gilmotech.nestspend.exception.ResourceNotFoundException;
import be.gilmotech.nestspend.security.CurrentUserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Service for classification rules and auto-categorization suggestions.
 */
@Service
public class ClassificationService {

    private final ClassificationRuleRepository ruleRepository;
    private final TransactionRepository transactionRepository;
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

    // Learning algorithm thresholds
    private static final double LEARNING_MIN_RATIO = 0.85; // 85% stability ratio
    private static final int LEARNING_MIN_TRANSACTIONS = 5; // Minimum 5 transactions
    private static final int LEARNING_AUTO_PRIORITY = 50; // Lower than USER rules (default 100)

    /**
     * Default classification rules for common merchants in Belgium/France.
     * Pattern format: field, matchType, pattern, categoryName, priority, confidence
     */
    private static final List<DefaultRule> DEFAULT_RULES = List.of(
            // === ALIMENTATION (Food & Grocery) - Priority 100+ ===
            // Belgium supermarkets
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "DELHAIZE", "Alimentation", 100, 85),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "COLRUYT", "Alimentation", 100, 85),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "ALBERT HEIJN", "Alimentation", 100, 85),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "ALDI", "Alimentation", 100, 85),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "LIDL", "Alimentation", 100, 85),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "CARREFOUR", "Alimentation", 100, 85),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "INTERMARCHE", "Alimentation", 100, 85),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "MATCH", "Alimentation", 99, 80),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "SPAR", "Alimentation", 99, 80),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "PROXY DELHAIZE", "Alimentation", 100, 85),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "AD DELHAIZE", "Alimentation", 100, 85),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "OKAY", "Alimentation", 99, 80),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "BIO PLANET", "Alimentation", 100, 85),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "CORA", "Alimentation", 100, 85),
            
            // France supermarkets
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "LECLERC", "Alimentation", 100, 85),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "AUCHAN", "Alimentation", 100, 85),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "CASINO", "Alimentation", 99, 80),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "MONOPRIX", "Alimentation", 100, 85),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "FRANPRIX", "Alimentation", 100, 85),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "PICARD", "Alimentation", 100, 85),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "SUPER U", "Alimentation", 100, 85),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "SYSTEME U", "Alimentation", 100, 85),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "BIOCOOP", "Alimentation", 100, 85),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "NATURALIA", "Alimentation", 100, 85),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "GRAND FRAIS", "Alimentation", 100, 85),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "LEADER PRICE", "Alimentation", 100, 85),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "NETTO", "Alimentation", 99, 80),
            
            // Bakeries & specialty
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "BOULANGERIE", "Alimentation", 95, 80),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "BOUCHERIE", "Alimentation", 95, 80),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "FROMAGERIE", "Alimentation", 95, 80),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "PRIMEUR", "Alimentation", 95, 80),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "PATISSERIE", "Alimentation", 95, 80),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "EPICERIE", "Alimentation", 95, 80),
            
            // Fast food & restaurants
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "MCDONALDS", "Alimentation", 90, 85),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "MC DONALDS", "Alimentation", 90, 85),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "BURGER KING", "Alimentation", 90, 85),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "QUICK", "Alimentation", 90, 85),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "KFC", "Alimentation", 90, 85),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "SUBWAY", "Alimentation", 90, 85),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "DOMINOS", "Alimentation", 90, 85),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "PIZZA HUT", "Alimentation", 90, 85),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "STARBUCKS", "Alimentation", 90, 85),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "PANOS", "Alimentation", 90, 85),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "PAUL", "Alimentation", 89, 75),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "EXKI", "Alimentation", 90, 85),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "DELIVEROO", "Alimentation", 90, 85),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "UBER EATS", "Alimentation", 90, 85),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "UBEREATS", "Alimentation", 90, 85),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "JUST EAT", "Alimentation", 90, 85),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "TAKEAWAY", "Alimentation", 90, 85),
            
            // === TRANSPORT - Priority 90+ ===
            // Fuel stations
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "TOTAL ENERGIES", "Transport", 95, 85),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "TOTALENERGIES", "Transport", 95, 85),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "TOTAL", "Transport", 94, 75),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "SHELL", "Transport", 95, 85),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "Q8", "Transport", 95, 85),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "ESSO", "Transport", 95, 85),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "TEXACO", "Transport", 95, 85),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "BP", "Transport", 94, 75),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "LUKOIL", "Transport", 95, 85),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "GULF", "Transport", 95, 85),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "STATION SERVICE", "Transport", 94, 80),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "CARBURANT", "Transport", 94, 80),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "ESSENCE", "Transport", 93, 75),
            
            // Public transport Belgium
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "SNCB", "Transport", 95, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "NMBS", "Transport", 95, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "STIB", "Transport", 95, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "MIVB", "Transport", 95, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "DE LIJN", "Transport", 95, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "TEC", "Transport", 95, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "DELIJN", "Transport", 95, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "THALYS", "Transport", 95, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "EUROSTAR", "Transport", 95, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "FLIXBUS", "Transport", 95, 90),
            
            // Public transport France
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "SNCF", "Transport", 95, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "RATP", "Transport", 95, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "NAVIGO", "Transport", 95, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "OUIGO", "Transport", 95, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "TGV", "Transport", 95, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "INOUI", "Transport", 95, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "METRO", "Transport", 93, 75),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "TRAMWAY", "Transport", 93, 80),
            
            // Parking & tolls
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "PARKING", "Transport", 90, 85),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "INDIGO", "Transport", 90, 85),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "EFFIA", "Transport", 90, 85),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "VINCI PARK", "Transport", 90, 85),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "AUTOROUTE", "Transport", 90, 85),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "PEAGE", "Transport", 90, 85),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "TELEPASS", "Transport", 90, 85),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "LIBER-T", "Transport", 90, 85),
            
            // Rideshare & taxi
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "UBER", "Transport", 90, 85),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "BOLT", "Transport", 90, 85),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "HEETCH", "Transport", 90, 85),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "FREENOW", "Transport", 90, 85),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "TAXI", "Transport", 89, 80),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "KAPTEN", "Transport", 90, 85),
            
            // Bike & scooter sharing
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "VILLO", "Transport", 90, 85),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "VELIB", "Transport", 90, 85),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "LIME", "Transport", 90, 85),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "TIER", "Transport", 90, 85),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "BIRD", "Transport", 90, 85),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "VOI", "Transport", 90, 85),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "DOTT", "Transport", 90, 85),
            
            // Car rental
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "HERTZ", "Transport", 90, 85),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "AVIS", "Transport", 90, 85),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "EUROPCAR", "Transport", 90, 85),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "SIXT", "Transport", 90, 85),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "ENTERPRISE", "Transport", 90, 85),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "CAMBIO", "Transport", 90, 85),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "DRIVY", "Transport", 90, 85),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "GETAROUND", "Transport", 90, 85),
            
            // === FACTURES (Bills & Utilities) - Priority 85+ ===
            // Telecom Belgium
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "PROXIMUS", "Factures", 90, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "ORANGE BELGIUM", "Factures", 90, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "TELENET", "Factures", 90, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "VOO", "Factures", 90, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "BASE", "Factures", 89, 85),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "MOBILE VIKINGS", "Factures", 90, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "SCARLET", "Factures", 90, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "EDPNET", "Factures", 90, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "UNLEASHED", "Factures", 90, 90),
            
            // Telecom France
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "ORANGE", "Factures", 88, 80),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "SFR", "Factures", 90, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "BOUYGUES TELECOM", "Factures", 90, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "FREE MOBILE", "Factures", 90, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "SOSH", "Factures", 90, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "RED BY SFR", "Factures", 90, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "B&YOU", "Factures", 90, 90),
            
            // Energy Belgium
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "ENGIE", "Factures", 90, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "LUMINUS", "Factures", 90, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "ELECTRABEL", "Factures", 90, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "LAMPIRIS", "Factures", 90, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "MEGA", "Factures", 88, 80),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "OCTA+", "Factures", 90, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "ENECO", "Factures", 90, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "ESSENT", "Factures", 90, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "BOLT ENERGY", "Factures", 90, 90),
            
            // Energy France
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "EDF", "Factures", 90, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "ENEDIS", "Factures", 90, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "GDF", "Factures", 90, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "GRDF", "Factures", 90, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "DIRECT ENERGIE", "Factures", 90, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "ENI", "Factures", 89, 85),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "VATTENFALL", "Factures", 90, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "EKWATEUR", "Factures", 90, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "ILEK", "Factures", 90, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "PLUM", "Factures", 88, 80),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "BUTAGAZ", "Factures", 90, 90),
            
            // Water
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "VIVAQUA", "Factures", 90, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "SWDE", "Factures", 90, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "VEOLIA", "Factures", 90, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "SUEZ", "Factures", 90, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "EAU DE PARIS", "Factures", 90, 90),
            
            // Internet/Cable
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "NETFLIX", "Factures", 85, 85),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "DISNEY+", "Factures", 85, 85),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "DISNEY PLUS", "Factures", 85, 85),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "SPOTIFY", "Factures", 85, 85),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "AMAZON PRIME", "Factures", 85, 85),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "APPLE MUSIC", "Factures", 85, 85),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "HBO", "Factures", 85, 85),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "CANAL+", "Factures", 85, 85),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "CANAL PLUS", "Factures", 85, 85),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "YOUTUBE PREMIUM", "Factures", 85, 85),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "DEEZER", "Factures", 85, 85),
            
            // === LOGEMENT (Housing) - Priority 80+ ===
            new DefaultRule(RuleField.COMMUNICATION, MatchType.CONTAINS, "LOYER", "Logement", 90, 90),
            new DefaultRule(RuleField.COMMUNICATION, MatchType.CONTAINS, "RENT", "Logement", 89, 85),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "IMMOBILIER", "Logement", 85, 80),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "SYNDIC", "Logement", 85, 85),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "AGENCE", "Logement", 80, 70),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "CHARGES", "Logement", 85, 80),
            
            // Home improvement
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "IKEA", "Logement", 85, 85),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "BRICO", "Logement", 85, 85),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "LEROY MERLIN", "Logement", 85, 85),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "CASTORAMA", "Logement", 85, 85),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "MR BRICOLAGE", "Logement", 85, 85),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "HUBO", "Logement", 85, 85),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "GAMMA", "Logement", 85, 85),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "ACTION", "Logement", 82, 75),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "HEMA", "Logement", 82, 75),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "BLOKKER", "Logement", 82, 75),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "CASA", "Logement", 82, 75),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "MAISON DU MONDE", "Logement", 85, 85),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "CONFORAMA", "Logement", 85, 85),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "BUT", "Logement", 82, 75),
            
            // === SANTE (Health) - Priority 80+ ===
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "PHARMACIE", "Santé", 90, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "APOTHEEK", "Santé", 90, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "MEDECIN", "Santé", 90, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "DOCTEUR", "Santé", 90, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "CABINET MEDICAL", "Santé", 90, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "HOPITAL", "Santé", 90, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "CLINIQUE", "Santé", 90, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "LABORATOIRE", "Santé", 88, 85),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "DENTISTE", "Santé", 90, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "OPTICIEN", "Santé", 88, 85),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "KRUIDVAT", "Santé", 85, 80),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "DI", "Santé", 80, 70),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "PARAPHARMACIE", "Santé", 88, 85),
            
            // Health insurance Belgium
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "MUTUALITE", "Santé", 90, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "MUTUELLE", "Santé", 90, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "PARTENAMUT", "Santé", 90, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "MC", "Santé", 80, 70),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "SOLIDARIS", "Santé", 90, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "HELAN", "Santé", 90, 90),
            
            // === LOISIRS (Entertainment & Leisure) - Priority 75+ ===
            // Cinema & entertainment
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "KINEPOLIS", "Loisirs", 85, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "UGC", "Loisirs", 85, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "PATHE", "Loisirs", 85, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "CINEMA", "Loisirs", 80, 80),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "BIOSCOOP", "Loisirs", 80, 80),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "GAUMONT", "Loisirs", 85, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "MK2", "Loisirs", 85, 90),
            
            // Theme parks
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "WALIBI", "Loisirs", 85, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "PLOPSALAND", "Loisirs", 85, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "BELLEWAERDE", "Loisirs", 85, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "BOBBEJAANLAND", "Loisirs", 85, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "DISNEYLAND", "Loisirs", 85, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "DISNEY", "Loisirs", 83, 80),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "PARC ASTERIX", "Loisirs", 85, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "FUTUROSCOPE", "Loisirs", 85, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "PUY DU FOU", "Loisirs", 85, 90),
            
            // Sports & fitness
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "BASIC FIT", "Loisirs", 85, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "FITNESS", "Loisirs", 80, 80),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "GYM", "Loisirs", 79, 75),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "DECATHLON", "Loisirs", 82, 85),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "GO SPORT", "Loisirs", 82, 85),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "SPORT 2000", "Loisirs", 82, 85),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "INTERSPORT", "Loisirs", 82, 85),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "FOOT LOCKER", "Loisirs", 82, 85),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "JDSPORTS", "Loisirs", 82, 85),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "PISCINE", "Loisirs", 80, 80),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "TENNIS", "Loisirs", 79, 75),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "GOLF", "Loisirs", 79, 75),
            
            // Gaming
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "PLAYSTATION", "Loisirs", 85, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "XBOX", "Loisirs", 85, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "NINTENDO", "Loisirs", 85, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "STEAM", "Loisirs", 85, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "GAME MANIA", "Loisirs", 85, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "MICROMANIA", "Loisirs", 85, 90),
            
            // Cultural
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "MUSEE", "Loisirs", 80, 85),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "MUSEUM", "Loisirs", 80, 85),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "THEATRE", "Loisirs", 80, 85),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "CONCERT", "Loisirs", 80, 85),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "OPERA", "Loisirs", 80, 85),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "FNAC", "Loisirs", 80, 80),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "MEDIAMARKT", "Loisirs", 80, 80),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "MEDIA MARKT", "Loisirs", 80, 80),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "BOULANGER", "Loisirs", 80, 80),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "DARTY", "Loisirs", 80, 80),
            
            // === SHOPPING - Priority 70+ ===
            // Fashion
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "ZALANDO", "Shopping", 85, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "ZARA", "Shopping", 85, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "H&M", "Shopping", 85, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "H & M", "Shopping", 85, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "PRIMARK", "Shopping", 85, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "C&A", "Shopping", 85, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "MANGO", "Shopping", 85, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "UNIQLO", "Shopping", 85, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "PULL & BEAR", "Shopping", 85, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "BERSHKA", "Shopping", 85, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "MASSIMO DUTTI", "Shopping", 85, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "STRADIVARIUS", "Shopping", 85, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "JULES", "Shopping", 84, 85),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "CELIO", "Shopping", 85, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "KIABI", "Shopping", 85, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "LA REDOUTE", "Shopping", 85, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "3 SUISSES", "Shopping", 85, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "CAMAIEU", "Shopping", 85, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "PROMOD", "Shopping", 85, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "NAF NAF", "Shopping", 85, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "ETAM", "Shopping", 85, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "CACHE CACHE", "Shopping", 85, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "JENNYFER", "Shopping", 85, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "PIMKIE", "Shopping", 85, 90),
            
            // Shoes
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "SARENZA", "Shopping", 85, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "SHOE DISCOUNT", "Shopping", 85, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "TORFS", "Shopping", 85, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "ERAM", "Shopping", 85, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "CHAUSSEA", "Shopping", 85, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "BESSON", "Shopping", 85, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "SAN MARINA", "Shopping", 85, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "ANDRE", "Shopping", 83, 80),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "MINELLI", "Shopping", 85, 90),
            
            // E-commerce
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "AMAZON", "Shopping", 80, 80),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "AMZN", "Shopping", 80, 80),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "BOL.COM", "Shopping", 85, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "COOLBLUE", "Shopping", 85, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "VINTED", "Shopping", 85, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "ALIEXPRESS", "Shopping", 85, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "WISH", "Shopping", 83, 80),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "EBAY", "Shopping", 83, 80),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "LEBONCOIN", "Shopping", 83, 80),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "2DEHANDS", "Shopping", 83, 80),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "2EMEMAIN", "Shopping", 83, 80),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "CDISCOUNT", "Shopping", 85, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "RUE DU COMMERCE", "Shopping", 85, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "SHOWROOMPRIVE", "Shopping", 85, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "VEEPEE", "Shopping", 85, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "VENTE PRIVEE", "Shopping", 85, 90),
            
            // Cosmetics & beauty
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "SEPHORA", "Shopping", 85, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "MARIONNAUD", "Shopping", 85, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "NOCIBE", "Shopping", 85, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "YVES ROCHER", "Shopping", 85, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "LUSH", "Shopping", 85, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "BODY SHOP", "Shopping", 85, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "KIKO", "Shopping", 85, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "DOUGLAS", "Shopping", 85, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "ICI PARIS XL", "Shopping", 85, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "RITUALS", "Shopping", 85, 90),
            
            // === EDUCATION - Priority 70+ ===
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "UNIVERSITE", "Éducation", 85, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "UNIVERSITY", "Éducation", 85, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "ECOLE", "Éducation", 85, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "SCHOOL", "Éducation", 85, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "COLLEGE", "Éducation", 83, 85),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "LYCEE", "Éducation", 85, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "CRECHE", "Éducation", 85, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "GARDERIE", "Éducation", 85, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "UCL", "Éducation", 85, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "ULB", "Éducation", 85, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "VUB", "Éducation", 85, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "KULEUVEN", "Éducation", 85, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "UGENT", "Éducation", 85, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "SORBONNE", "Éducation", 85, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "STANDAARD BOEKHANDEL", "Éducation", 80, 80),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "CULTURA", "Éducation", 80, 80),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "GIBERT", "Éducation", 80, 80),
            
            // Online learning
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "COURSERA", "Éducation", 85, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "UDEMY", "Éducation", 85, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "LINKEDIN LEARNING", "Éducation", 85, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "OPENCLASSROOMS", "Éducation", 85, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "DUOLINGO", "Éducation", 85, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "BABBEL", "Éducation", 85, 90),
            
            // === EPARGNE (Savings & Investment) - Priority 70+ ===
            // Belgium banks
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "BNP PARIBAS FORTIS", "Épargne", 85, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "ING", "Épargne", 82, 80),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "KBC", "Épargne", 85, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "BELFIUS", "Épargne", 85, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "ARGENTA", "Épargne", 85, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "CRELAN", "Épargne", 85, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "AXA BANK", "Épargne", 85, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "CBC", "Épargne", 84, 85),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "KEYTRADE", "Épargne", 85, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "BOLERO", "Épargne", 85, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "DEGIRO", "Épargne", 85, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "TRADE REPUBLIC", "Épargne", 85, 90),
            
            // France banks
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "BNP", "Épargne", 82, 80),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "SOCIETE GENERALE", "Épargne", 85, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "CREDIT AGRICOLE", "Épargne", 85, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "CREDIT MUTUEL", "Épargne", 85, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "CAISSE EPARGNE", "Épargne", 85, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "BANQUE POPULAIRE", "Épargne", 85, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "LA BANQUE POSTALE", "Épargne", 85, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "CIC", "Épargne", 84, 85),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "LCL", "Épargne", 85, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "BOURSORAMA", "Épargne", 85, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "FORTUNEO", "Épargne", 85, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "HELLO BANK", "Épargne", 85, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "N26", "Épargne", 85, 90),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "REVOLUT", "Épargne", 85, 90),
            
            // Savings & investment keywords
            new DefaultRule(RuleField.COMMUNICATION, MatchType.CONTAINS, "VIREMENT EPARGNE", "Épargne", 90, 90),
            new DefaultRule(RuleField.COMMUNICATION, MatchType.CONTAINS, "EPARGNE", "Épargne", 85, 85),
            new DefaultRule(RuleField.COMMUNICATION, MatchType.CONTAINS, "SAVINGS", "Épargne", 85, 85),
            new DefaultRule(RuleField.COMMUNICATION, MatchType.CONTAINS, "INVESTMENT", "Épargne", 85, 85),
            new DefaultRule(RuleField.COMMUNICATION, MatchType.CONTAINS, "PLACEMENT", "Épargne", 85, 85),
            
            // Insurance (often savings related)
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "ETHIAS", "Épargne", 85, 85),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "AG INSURANCE", "Épargne", 85, 85),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "AXA", "Épargne", 82, 80),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "ALLIANZ", "Épargne", 85, 85),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "GENERALI", "Épargne", 85, 85),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "MAIF", "Épargne", 85, 85),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "MACIF", "Épargne", 85, 85),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "MATMUT", "Épargne", 85, 85),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "GMF", "Épargne", 85, 85),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "MMA", "Épargne", 84, 80),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "MAAF", "Épargne", 85, 85),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "GROUPAMA", "Épargne", 85, 85),
            new DefaultRule(RuleField.MERCHANT, MatchType.CONTAINS, "ASSURANCE", "Épargne", 80, 75)
    );

    /**
     * Record for defining default classification rules.
     */
    private record DefaultRule(RuleField field, MatchType matchType, String pattern, 
                               String categoryName, int priority, int confidence) {}

    public ClassificationService(ClassificationRuleRepository ruleRepository,
                                  TransactionRepository transactionRepository,
                                  CategoryRepository categoryRepository,
                                  HouseholdRepository householdRepository,
                                  CurrentUserService currentUserService) {
        this.ruleRepository = ruleRepository;
        this.transactionRepository = transactionRepository;
        this.categoryRepository = categoryRepository;
        this.householdRepository = householdRepository;
        this.currentUserService = currentUserService;
    }

    // ==================== Default Rules Initialization ====================

    /**
     * Create default classification rules for a new household.
     * Rules cover common merchants in Belgium and France.
     *
     * @param household the household to create rules for
     */
    @Transactional
    public void createDefaultRules(be.gilmotech.nestspend.domain.entity.Household household) {
        // Get all categories for the household as a map by name
        List<Category> categories = categoryRepository.findByHouseholdId(household.getId());
        java.util.Map<String, Category> categoryMap = categories.stream()
                .collect(java.util.stream.Collectors.toMap(
                        Category::getName, 
                        c -> c,
                        (existing, replacement) -> existing // Keep first if duplicate names
                ));

        for (DefaultRule defaultRule : DEFAULT_RULES) {
            Category category = categoryMap.get(defaultRule.categoryName());
            if (category == null) {
                // Skip rules for categories that don't exist
                continue;
            }

            // Check if rule already exists (by pattern and field)
            if (ruleRepository.existsByHouseholdIdAndPatternIgnoreCaseAndField(
                    household.getId(), defaultRule.pattern(), defaultRule.field())) {
                continue; // Skip duplicate rules
            }

            ClassificationRule rule = ClassificationRule.builder()
                    .household(household)
                    .field(defaultRule.field())
                    .matchType(defaultRule.matchType())
                    .pattern(defaultRule.pattern())
                    .category(category)
                    .priority(defaultRule.priority())
                    .confidence(defaultRule.confidence())
                    .enabled(true)
                    .source(RuleSource.AUTO)
                    .build();

            ruleRepository.save(rule);
        }
    }

    /**
     * Initialize default classification rules for the current user's household if none exist.
     * This is useful for existing users who registered before the auto-categorization feature was added.
     *
     * @return the number of rules created
     */
    @Transactional
    public int initializeDefaultRulesIfNeeded() {
        UUID householdId = currentUserService.getCurrentHouseholdId();
        
        // Check if any rules already exist
        List<ClassificationRule> existingRules = ruleRepository.findByHouseholdId(householdId);
        if (!existingRules.isEmpty()) {
            return 0; // Rules already exist, don't create duplicates
        }

        // Get the household entity
        var household = householdRepository.findById(householdId)
                .orElseThrow(() -> new ResourceNotFoundException("Household not found"));

        // Create default rules
        createDefaultRules(household);

        // Return count of newly created rules
        return ruleRepository.findByHouseholdId(householdId).size();
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

    // ==================== Auto-Learning Algorithm ====================

    /**
     * Learn classification rules from transaction history.
     * Analyzes categorized transactions to identify stable merchant-category patterns
     * and creates or updates AUTO rules based on the analysis.
     * 
     * Algorithm:
     * 1. Group transactions by normalized merchant name
     * 2. For each merchant, calculate the dominant category ratio
     * 3. Create/update AUTO rules if ratio >= 85% and count >= 5 transactions
     * 
     * USER rules are never modified. AUTO rules have lower priority than USER rules.
     *
     * @return LearningResult containing statistics about created/updated rules
     */
    @Transactional
    public LearningResult learnFromHistory() {
        UUID householdId = currentUserService.getCurrentHouseholdId();
        var household = householdRepository.findById(householdId)
                .orElseThrow(() -> new ResourceNotFoundException("Household not found"));

        // Get merchant-category statistics from transaction history
        List<Object[]> stats = transactionRepository.getMerchantCategoryStats(householdId);
        
        // Build a map of categories by ID for quick lookup
        Map<UUID, Category> categoryMap = new HashMap<>();
        for (Category cat : categoryRepository.findByHouseholdId(householdId)) {
            categoryMap.put(cat.getId(), cat);
        }

        // Group statistics by merchant
        Map<String, List<MerchantCategoryStat>> merchantStats = new LinkedHashMap<>();
        int totalTransactions = 0;
        
        for (Object[] row : stats) {
            String merchant = (String) row[0];
            UUID categoryId = (UUID) row[1];
            String categoryName = (String) row[2];
            long count = (Long) row[3];
            
            merchantStats.computeIfAbsent(merchant, k -> new ArrayList<>())
                    .add(new MerchantCategoryStat(categoryId, categoryName, (int) count));
            totalTransactions += count;
        }

        List<LearningResult.RuleDetail> createdRules = new ArrayList<>();
        List<LearningResult.RuleDetail> updatedRules = new ArrayList<>();
        int merchantsIgnored = 0;

        // Analyze each merchant
        for (Map.Entry<String, List<MerchantCategoryStat>> entry : merchantStats.entrySet()) {
            String merchant = entry.getKey();
            List<MerchantCategoryStat> categoryStats = entry.getValue();
            
            // Calculate total transactions for this merchant
            int totalForMerchant = categoryStats.stream()
                    .mapToInt(MerchantCategoryStat::count)
                    .sum();
            
            // Check minimum transaction threshold
            if (totalForMerchant < LEARNING_MIN_TRANSACTIONS) {
                merchantsIgnored++;
                continue;
            }
            
            // Find the dominant category
            MerchantCategoryStat dominant = categoryStats.get(0); // Already sorted by count DESC
            double ratio = (double) dominant.count() / totalForMerchant;
            
            // Check minimum ratio threshold
            if (ratio < LEARNING_MIN_RATIO) {
                merchantsIgnored++;
                continue;
            }
            
            // Check if a USER rule already exists for this merchant
            // USER rules should never be overwritten by AUTO learning
            if (ruleRepository.existsByHouseholdIdAndPatternIgnoreCaseAndField(
                    householdId, merchant, RuleField.MERCHANT)) {
                // Check if it's a USER rule
                Optional<ClassificationRule> existingRule = ruleRepository
                        .findByHouseholdIdAndPatternIgnoreCaseAndFieldAndSource(
                                householdId, merchant, RuleField.MERCHANT, RuleSource.USER);
                if (existingRule.isPresent()) {
                    // USER rule exists, skip this merchant
                    merchantsIgnored++;
                    continue;
                }
            }
            
            // Calculate confidence score (ratio * 100)
            int confidence = (int) Math.round(ratio * 100);
            
            // Get the category entity
            Category category = categoryMap.get(dominant.categoryId());
            if (category == null) {
                merchantsIgnored++;
                continue;
            }
            
            // Check if an AUTO rule already exists
            Optional<ClassificationRule> existingAutoRule = ruleRepository
                    .findByHouseholdIdAndPatternIgnoreCaseAndFieldAndSource(
                            householdId, merchant, RuleField.MERCHANT, RuleSource.AUTO);
            
            if (existingAutoRule.isPresent()) {
                // Update existing AUTO rule
                ClassificationRule rule = existingAutoRule.get();
                rule.setCategory(category);
                rule.setConfidence(confidence);
                ruleRepository.save(rule);
                
                updatedRules.add(new LearningResult.RuleDetail(
                        merchant,
                        dominant.categoryName(),
                        confidence,
                        totalForMerchant
                ));
            } else {
                // Create new AUTO rule
                ClassificationRule rule = ClassificationRule.builder()
                        .household(household)
                        .field(RuleField.MERCHANT)
                        .matchType(MatchType.CONTAINS)
                        .pattern(merchant)
                        .category(category)
                        .priority(LEARNING_AUTO_PRIORITY)
                        .confidence(confidence)
                        .enabled(true)
                        .source(RuleSource.AUTO)
                        .build();
                
                ruleRepository.save(rule);
                
                createdRules.add(new LearningResult.RuleDetail(
                        merchant,
                        dominant.categoryName(),
                        confidence,
                        totalForMerchant
                ));
            }
        }

        return new LearningResult(
                createdRules.size(),
                updatedRules.size(),
                merchantsIgnored,
                totalTransactions,
                createdRules,
                updatedRules
        );
    }

    /**
     * Record for internal use during learning algorithm.
     */
    private record MerchantCategoryStat(UUID categoryId, String categoryName, int count) {}

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
