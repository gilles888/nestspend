# NestSpend API — Feature 09: Dashboard mensuel (agrégations)

## Endpoint
- GET /api/dashboard?month=YYYY-MM

## Réponse attendue (exemple)
{
"month": "2025-12",
"totalIncomeCents": 0,
"totalExpenseCents": 0,
"netCents": 0,
"expensesByCategory": [
{ "categoryId": "...", "categoryName": "Courses", "amountCents": 12345 }
]
}

## Règles
- scoping household obligatoire
- performant: requêtes SQL agrégées

## Tâches
1) DashboardService:
    - calcul total income/expense
    - breakdown par catégorie (expenses)
2) Controller + DTO response
3) Tests avec seed data

## DoD
- endpoint renvoie valeurs correctes
