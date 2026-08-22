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

## APK & mises à jour (GitHub Actions + Obtainium)
- Chaque push sur `main` construit un APK **release signé** (artifact `EuroWidget-apk` dans l'onglet Actions).
- Chaque tag `vX.Y.Z` publie en plus une **Release GitHub** avec l'APK attaché :
  `git tag v0.3.0 && git push origin v0.3.0`
- Sur le téléphone : **Obtainium** → Ajouter une app → URL `https://github.com/THEclearboy/eurowinget`.
  Il détecte les nouvelles releases et propose la mise à jour (installation par-dessus, données conservées).
- Le `versionCode` = numéro de run CI (toujours croissant) ; `versionName` = le tag.
- Signature : keystore PKCS12 stocké dans les secrets GitHub (`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`).
  Ne jamais le changer, sinon Android refusera la mise à jour (désinstallation obligatoire).
  En local : créer `keystore.properties` (ignoré par git) avec `KEYSTORE_FILE=...`, `KEYSTORE_PASSWORD=...`, `KEY_ALIAS=...`, `KEY_PASSWORD=...`.
