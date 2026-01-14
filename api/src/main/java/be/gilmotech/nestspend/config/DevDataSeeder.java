package be.gilmotech.nestspend.config;

import be.gilmotech.nestspend.domain.entity.Account;
import be.gilmotech.nestspend.domain.entity.Category;
import be.gilmotech.nestspend.domain.entity.Household;
import be.gilmotech.nestspend.domain.entity.Transaction;
import be.gilmotech.nestspend.domain.entity.User;
import be.gilmotech.nestspend.domain.enums.AccountType;
import be.gilmotech.nestspend.domain.enums.TransactionType;
import be.gilmotech.nestspend.domain.enums.UserRole;
import be.gilmotech.nestspend.domain.repository.AccountRepository;
import be.gilmotech.nestspend.domain.repository.CategoryRepository;
import be.gilmotech.nestspend.domain.repository.HouseholdRepository;
import be.gilmotech.nestspend.domain.repository.TransactionRepository;
import be.gilmotech.nestspend.domain.repository.UserRepository;
import be.gilmotech.nestspend.service.CategoryService;
import be.gilmotech.nestspend.service.ClassificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

/**
 * Data seeder for development environment.
 * Creates default user, accounts, and sample transactions.
 * Active only with the "dev" profile.
 * 
 * <p>WARNING: This class contains hardcoded development credentials.
 * These are intentionally included for development/testing convenience.
 * This seeder is ONLY active in the 'dev' profile and should NEVER be
 * used in production environments.</p>
 */
@Component
@Profile("dev")
public class DevDataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);

    // Development credentials - intentionally hardcoded for dev/test convenience
    // SECURITY NOTE: This seeder only runs with 'dev' profile
    private static final String DEFAULT_EMAIL = "gilmoreau@hotmail.com";
    private static final String DEFAULT_PASSWORD = "Gilmo=270188"; // NOSONAR - dev credentials
    private static final String DEFAULT_DISPLAY_NAME = "Gilles Moreau";
    private static final String DEFAULT_HOUSEHOLD_NAME = "Moreau Family";

    private final UserRepository userRepository;
    private final HouseholdRepository householdRepository;
    private final AccountRepository accountRepository;
    private final CategoryRepository categoryRepository;
    private final TransactionRepository transactionRepository;
    private final PasswordEncoder passwordEncoder;
    private final CategoryService categoryService;
    private final ClassificationService classificationService;

    private final Random random = new Random(42); // Fixed seed for reproducible data

    public DevDataSeeder(UserRepository userRepository,
                         HouseholdRepository householdRepository,
                         AccountRepository accountRepository,
                         CategoryRepository categoryRepository,
                         TransactionRepository transactionRepository,
                         PasswordEncoder passwordEncoder,
                         CategoryService categoryService,
                         ClassificationService classificationService) {
        this.userRepository = userRepository;
        this.householdRepository = householdRepository;
        this.accountRepository = accountRepository;
        this.categoryRepository = categoryRepository;
        this.transactionRepository = transactionRepository;
        this.passwordEncoder = passwordEncoder;
        this.categoryService = categoryService;
        this.classificationService = classificationService;
    }

    @Override
    @Transactional
    public void run(String... args) {
        // Check if default user already exists
        if (userRepository.findByEmail(DEFAULT_EMAIL).isPresent()) {
            log.info("Dev data already seeded. Skipping.");
            return;
        }

        log.info("Seeding development data...");

        // Create household
        Household household = Household.builder()
                .name(DEFAULT_HOUSEHOLD_NAME)
                .build();
        household = householdRepository.save(household);
        log.info("Created household: {}", household.getName());

        // Create default categories
        categoryService.createDefaultCategories(household);
        log.info("Created default categories");

        // Create default classification rules
        classificationService.createDefaultRules(household);
        log.info("Created default classification rules");

        // Create user
        User user = User.builder()
                .household(household)
                .email(DEFAULT_EMAIL)
                .passwordHash(passwordEncoder.encode(DEFAULT_PASSWORD))
                .displayName(DEFAULT_DISPLAY_NAME)
                .role(UserRole.ADMIN)
                .build();
        user = userRepository.save(user);
        log.info("Created user: {} ({})", user.getDisplayName(), user.getEmail());

        // Create bank accounts
        Account keytradeAccount = Account.builder()
                .household(household)
                .name("Keytrade - commun")
                .type(AccountType.BANK)
                .build();
        keytradeAccount = accountRepository.save(keytradeAccount);
        log.info("Created account: {} ({})", keytradeAccount.getName(), keytradeAccount.getType());

        Account ingAccount = Account.builder()
                .household(household)
                .name("ING - commun")
                .type(AccountType.BANK)
                .build();
        ingAccount = accountRepository.save(ingAccount);
        log.info("Created account: {} ({})", ingAccount.getName(), ingAccount.getType());

        // Get categories for transactions
        List<Category> categories = categoryRepository.findByHouseholdId(household.getId());
        Map<String, Category> categoryMap = categories.stream()
                .collect(Collectors.toMap(Category::getName, c -> c));

        // Generate 6 months of transactions
        generateTransactions(household, user, keytradeAccount, ingAccount, categoryMap);

        log.info("Development data seeding completed successfully!");
        log.info("======================================");
        log.info("DEV MODE - Default user credentials:");
        log.info("  Email: {}", DEFAULT_EMAIL);
        log.info("  Password: See DevDataSeeder.DEFAULT_PASSWORD");
        log.info("======================================");
    }

    private void generateTransactions(Household household, User user,
                                      Account keytradeAccount, Account ingAccount,
                                      Map<String, Category> categoryMap) {
        LocalDate today = LocalDate.now();
        LocalDate startDate = today.minusMonths(6);
        
        // Calculate total days in the period (ensure at least 1 day)
        long totalDays = java.time.temporal.ChronoUnit.DAYS.between(startDate, today);
        if (totalDays <= 0) {
            totalDays = 180; // fallback to approximately 6 months
        }

        int transactionCount = 0;

        // Generate recurring income (salary) - once per month on ING
        for (int month = 0; month < 6; month++) {
            LocalDate salaryDate = startDate.plusMonths(month).withDayOfMonth(
                    Math.min(25, startDate.plusMonths(month).lengthOfMonth()));
            createTransaction(household, user, ingAccount, categoryMap.get("Autres"),
                    TransactionType.INCOME, salaryDate, 450000L, "Employeur SA", "Salaire mensuel");
            transactionCount++;
        }

        // Generate various expenses distributed over 6 months
        List<TransactionTemplate> expenseTemplates = List.of(
                // Alimentation - frequent
                new TransactionTemplate("Alimentation", "DELHAIZE", 4500L, 12000L, 8, keytradeAccount),
                new TransactionTemplate("Alimentation", "COLRUYT", 6000L, 15000L, 6, ingAccount),
                new TransactionTemplate("Alimentation", "ALDI", 3500L, 8000L, 6, keytradeAccount),
                new TransactionTemplate("Alimentation", "CARREFOUR", 2500L, 7000L, 4, ingAccount),
                new TransactionTemplate("Alimentation", "BOULANGERIE DU COIN", 500L, 1500L, 12, keytradeAccount),
                new TransactionTemplate("Alimentation", "MCDONALDS", 1200L, 3500L, 3, keytradeAccount),
                new TransactionTemplate("Alimentation", "DELIVEROO", 2000L, 4500L, 4, ingAccount),

                // Transport
                new TransactionTemplate("Transport", "TOTAL ENERGIES", 5000L, 9500L, 4, keytradeAccount),
                new TransactionTemplate("Transport", "SHELL", 4500L, 8500L, 3, ingAccount),
                new TransactionTemplate("Transport", "SNCB", 2500L, 5500L, 2, keytradeAccount),
                new TransactionTemplate("Transport", "PARKING INTERPARKING", 300L, 800L, 6, keytradeAccount),

                // Logement - monthly bills
                new TransactionTemplate("Logement", "PROPRIÉTAIRE LOYER", 85000L, 85000L, 1, ingAccount),
                new TransactionTemplate("Logement", "IKEA", 5000L, 25000L, 1, keytradeAccount),
                new TransactionTemplate("Logement", "BRICO", 1500L, 8000L, 2, keytradeAccount),

                // Factures - monthly
                new TransactionTemplate("Factures", "PROXIMUS", 6500L, 7500L, 1, ingAccount),
                new TransactionTemplate("Factures", "ENGIE ELECTRABEL", 12000L, 18000L, 1, ingAccount),
                new TransactionTemplate("Factures", "NETFLIX", 1599L, 1599L, 1, keytradeAccount),
                new TransactionTemplate("Factures", "SPOTIFY", 999L, 999L, 1, keytradeAccount),

                // Santé
                new TransactionTemplate("Santé", "PHARMACIE CENTRALE", 1500L, 6000L, 2, keytradeAccount),
                new TransactionTemplate("Santé", "DR DUPONT", 2500L, 5000L, 1, keytradeAccount),

                // Loisirs
                new TransactionTemplate("Loisirs", "KINEPOLIS", 1200L, 3500L, 2, keytradeAccount),
                new TransactionTemplate("Loisirs", "BASIC FIT", 2499L, 2499L, 1, keytradeAccount),
                new TransactionTemplate("Loisirs", "FNAC", 2000L, 8000L, 1, ingAccount),
                new TransactionTemplate("Loisirs", "DECATHLON", 3000L, 12000L, 1, keytradeAccount),

                // Shopping
                new TransactionTemplate("Shopping", "ZALANDO", 3500L, 12000L, 2, keytradeAccount),
                new TransactionTemplate("Shopping", "H&M", 2500L, 8000L, 2, ingAccount),
                new TransactionTemplate("Shopping", "AMAZON", 1500L, 8000L, 3, keytradeAccount),
                new TransactionTemplate("Shopping", "ACTION", 1000L, 3000L, 4, keytradeAccount),

                // Éducation
                new TransactionTemplate("Éducation", "STANDAARD BOEKHANDEL", 1500L, 4500L, 1, keytradeAccount),

                // Épargne - monthly transfer
                new TransactionTemplate("Épargne", "VIREMENT ÉPARGNE", 25000L, 50000L, 1, ingAccount)
        );

        // Generate transactions for each template
        for (TransactionTemplate template : expenseTemplates) {
            Category category = categoryMap.get(template.categoryName);
            if (category == null) {
                log.warn("Category not found: {}", template.categoryName);
                continue;
            }

            // Generate transactions spread over 6 months
            int totalOccurrences = template.monthlyFrequency * 6;
            for (int i = 0; i < totalOccurrences; i++) {
                // Random day within the 6 month period
                int daysOffset = random.nextInt((int) totalDays);
                LocalDate txDate = startDate.plusDays(daysOffset);

                // Random amount within range
                long amount = template.minAmountCents + 
                        (long) (random.nextDouble() * (template.maxAmountCents - template.minAmountCents));

                createTransaction(household, user, template.account, category,
                        TransactionType.EXPENSE, txDate, amount, template.merchant, null);
                transactionCount++;
            }
        }

        // Add some income items (reimbursements, transfers, etc.)
        List<TransactionTemplate> incomeTemplates = List.of(
                new TransactionTemplate("Autres", "REMBOURSEMENT MUTUELLE", 2000L, 8000L, 1, ingAccount),
                new TransactionTemplate("Autres", "REMBOURSEMENT IMPOTS", 15000L, 35000L, 1, ingAccount)
        );

        for (TransactionTemplate template : incomeTemplates) {
            Category category = categoryMap.get(template.categoryName);
            if (category == null) continue;

            for (int month = 0; month < 6; month++) {
                if (random.nextDouble() < 0.3) { // 30% chance per month
                    int dayOfMonth = random.nextInt(28) + 1;
                    LocalDate txDate = startDate.plusMonths(month).withDayOfMonth(dayOfMonth);
                    long amount = template.minAmountCents +
                            (long) (random.nextDouble() * (template.maxAmountCents - template.minAmountCents));
                    
                    createTransaction(household, user, template.account, category,
                            TransactionType.INCOME, txDate, amount, template.merchant, "Remboursement");
                    transactionCount++;
                }
            }
        }

        log.info("Generated {} transactions over 6 months", transactionCount);
    }

    private void createTransaction(Household household, User user, Account account,
                                   Category category, TransactionType type,
                                   LocalDate txDate, long amountCents,
                                   String merchant, String note) {
        Transaction transaction = Transaction.builder()
                .household(household)
                .createdBy(user)
                .account(account)
                .category(category)
                .type(type)
                .txDate(txDate)
                .amountCents(amountCents)
                .merchant(merchant)
                .note(note)
                .build();
        transactionRepository.save(transaction);
    }

    /**
     * Template for generating transaction data.
     */
    private record TransactionTemplate(
            String categoryName,
            String merchant,
            long minAmountCents,
            long maxAmountCents,
            int monthlyFrequency,
            Account account
    ) {}
}
