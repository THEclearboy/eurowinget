# EuroWidget — Nothing Phone

Widget écran d'accueil (4×2) : échelle de repères en € pour la devise choisie + taux BCE du jour.
Tap sur le widget → ouvre le convertisseur complet.

## Compiler
1. Android Studio (Ladybug ou plus récent) → File > Open → ce dossier.
2. Laisser Gradle synchroniser (télécharge Glance, Compose, WorkManager).
3. Run ▶ sur le Nothing Phone (débogage USB activé) ou Build > Build APK.

## Installer le widget
Appui long sur l'écran d'accueil → Widgets → EUR → glisser. Redimensionnable.

## Changer la devise du widget
Ouvre l'app, choisis une pastille : le widget suit.

## Typo dot-matrix (optionnel)
Glisser un .ttf dans `app/src/main/res/font/` (ex. doto_black.ttf, licence OFL, fonts.google.com/specimen/Doto)
puis dans MainActivity.kt : `private val Mono = FontFamily(Font(R.font.doto_black))`.
Le widget Glance reste en monospace système (limite Android des RemoteViews).

## Taux
- Source : api.frankfurter.dev (BCE), rafraîchi toutes les 6 h + à chaque ouverture de l'app.
- Hors-ligne : taux intégrés dans Rates.kt (CURRENCIES) ou saisie manuelle depuis l'app.
- Ajouter une devise : une ligne dans CURRENCIES, c'est tout.

## APK sans Android Studio (GitHub Actions)
1. Créer un dépôt GitHub (privé ok), y pousser ce dossier.
2. Onglet Actions → "Build APK" tourne seul (~4 min).
3. Ouvrir le run → Artifacts → télécharger `EuroWidget-debug.apk` (zip) → installer.
Sur Android : autoriser "Installer des applications inconnues" pour le navigateur si demandé.
