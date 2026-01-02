# WEB-03 — Sécurité front (JWT)

## Objectif
Sécuriser l’accès aux routes et aux appels API.

## Tâches
1) AuthService:
    - stocker le token JWT
    - supprimer le token au logout
2) HttpInterceptor:
    - ajouter Authorization: Bearer <token>
3) AuthGuard:
    - protéger les routes privées
4) Logout utilisateur

## DoD
- Routes protégées
- Session conservée après refresh
