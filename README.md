# Zeilersforum-app

Android-app voor [zeilersforum.nl](https://zeilersforum.nl). Het is een nieuwe versie (3.x) die de oorspronkelijke app uit 2016–2017 vervangt.

## Functies
- Tabbladen **Recent** (Recente discussies, periode instelbaar: sinds laatste bezoek, uren, week, maand, jaar of alles), **Forum** (categorieën en subcategorieën), **Zoeken** (hele bericht of alleen titels; afgelopen jaar of alles, want alles doorzoeken duurt op het forum zo'n 20 seconden) en **Mijn** (meegedaan / zelf gestart)
- Nieuw onderwerp starten vanuit een categorie
- Onderwerpen lezen met foto's, citaten, smileys (als emoji), video-links en bijlagen
- Volgorde van berichten instelbaar: **oud → nieuw** of **nieuw → oud** (knop ⇅ in een onderwerp, of via Instellingen)
- Verder waar je was gebleven: een onderwerp opent weer bij het bericht waar je was, met "Naar het begin" of "Naar het nieuwste" en een knop voor de pagina's daarboven. Per volgorde aan of uit te zetten onder Instellingen → Berichten (standaard alleen bij oud → nieuw). Links naar een bepaald bericht springen naar dat bericht
- Naar een bepaalde pagina springen
- Fotoviewer met zoomen en vegen
- "Wie leest er mee?" bovenaan een onderwerp (als je bent ingelogd)
- Privéberichten (uddeIM): postvak in, verzonden, lezen, beantwoorden, nieuw bericht en verwijderen, met teller voor ongelezen berichten
- Foto's in berichten: altijd laden, alleen via wifi, of pas na een tik op "Foto bekijken"
- Instelbaar app-icoon (twaalf varianten met de twee zeilen) via Instellingen → App-icoon
- Inloggen (wachtwoord versleuteld met de Android Keystore), automatisch opnieuw inloggen
- Reageren en citeren, met bijlagen: foto's (verkleind tot max. 1024 px, net als op het forum zelf) en bestanden van de typen die het forum toestaat (pdf, gpx, docx, xlsx, zip en meer; max. 6 per bericht); een foto zet je met "Invoegen" op de plek van de cursor in je tekst
- Opmaakbalk bij het berichtveld (vet, cursief, onderstrepen, doorhalen, citaat, link, lijsten, kleur, grootte, en onder "Meer" afbeelding, video, code, spoiler en centreren), de smileys van het forum, en een Voorbeeld dat het forum zelf opmaakt. De opmaakbalk staat onderaan (boven het toetsenbord) of bovenaan: Instellingen → Berichtveld
- Eigen berichten bewerken en verwijderen, zolang de site dat toestaat; bij bewerken kun je ook bijlagen weghalen of toevoegen
- Forumlinks openen in de app
- Updates: controleert (desgewenst dagelijks) de releases op GitHub, downloadt de nieuwe APK, controleert controlesom en sleutel en start de installatie

## Installeren
De app staat niet in de Play Store. Je installeert hem één keer zelf; daarna werkt hij zichzelf bij.

Je hebt een Android-telefoon of -tablet nodig met **Android 8 of nieuwer**. De app past zich aan het kleurenthema van je toestel aan en wordt donker als je toestel op donker staat.

### De eerste keer
1. Open op je Android-toestel in Chrome de [nieuwste release](https://github.com/kwillems/Zeilersforum/releases/latest) en tik op het bestand `zeilersforum-x.y.z.apk`. Het komt in **Downloads**.
2. Tik op de melding van de download (of open het bestand in Downloads).
3. Android meldt dat Chrome geen onbekende apps mag installeren. Tik op **Instellingen**, zet **Toestaan van deze bron** aan en ga terug.
4. Tik op **Installeren**. Android controleert de app (Play Protect) en installeert hem.
5. Tik op **Openen**.

Je kunt daarna `zeilersforum-x.y.z.apk` uit Downloads verwijderen, en Chrome de toestemming weer afnemen via **Instellingen → Apps → Speciale app-toegang → Onbekende apps installeren**.

Heb je nog de oude app (2.x)? Die heeft een andere pakketnaam, blijft gewoon naast de nieuwe staan en kun je zelf verwijderen.

### Daarna: bijwerken vanuit de app
1. Eens per dag kijkt de app bij het openen of er een nieuwe versie is (uit te zetten bij **Instellingen → Updates**; daar kun je ook zelf controleren). Is die er, dan staat bovenaan **Nieuwe versie x.y.z**. Tik erop.
2. Tik op **Downloaden**. De app downloadt de nieuwe versie en controleert de controlesom en of hij met dezelfde sleutel is ondertekend.
3. *Alleen de eerste keer:* tik op **Toestemming geven**, zet **Toestaan van deze bron** aan voor Zeilersforum en ga terug.
4. Tik op **Installeren** en bevestig bij Android met **Updaten**. De app sluit, wordt bijgewerkt en je tikt op **Openen**. Instellingen en inlog blijven bewaard.

## Bijwerken
Nieuwe versies installeer je via **Instellingen → Updates** in de app, of door de nieuwste APK van [Releases](https://github.com/kwillems/Zeilersforum/releases/latest) te openen.

**Let op bij de overstap naar het instelbare app-icoon** (na versie 3.0.5): de app krijgt standaard het icoon "Ballon". Op sommige toestellen haalt Android bij deze update de snelkoppeling van de app van het startscherm weg. Zet de app dan opnieuw op je startscherm vanuit de app-lijst. Hetzelfde kan gebeuren als je later een ander icoon kiest.

## Bouwen
- Kotlin, Jetpack Compose en Material 3 (Material You, donkere modus, edge-to-edge)
- compileSdk/targetSdk 37 (Android 17), minSdk 26 (Android 8)
- Pakketnaam `nl.kwillems.zeilersforum`

Vereist: JDK 21 en de Android SDK (platform `android-37.2`).

```bash
./gradlew assembleDebug      # testversie (nl.kwillems.zeilersforum.debug, naast de echte app)
./gradlew assembleRelease    # release-versie in app/build/outputs/apk/release/
./gradlew assembleRelease -PtestVersion=3.0.1   # om het updatemechanisme te testen
```

## Code
| Bestand | Inhoud |
|---|---|
| `data/ForumParser.kt` | Alles wat afhangt van de HTML-opbouw van de site (Joomla + Kunena, template blue_eagle) |
| `data/ForumRepository.kt` | Pagina's ophalen, inloggen (Community Builder), reageren |
| `data/PersistentCookieJar.kt` | Cookies bewaren zodat je ingelogd blijft |
| `data/CredentialStore.kt` | Versleutelde opslag van gebruikersnaam en wachtwoord |
| `ui/screens/*` | De schermen |

Verandert de website, dan hoeft meestal alleen `ForumParser.kt` aangepast te worden.

## Licentie
Copyright © 2026 Koen Willems

De code van deze app is vrije software onder de [GNU General Public License versie 3](LICENSE) (GPL-3.0). Je mag hem gebruiken, aanpassen en verspreiden, mits een aangepaste versie ook onder de GPL-3.0 en met broncode beschikbaar komt. De software wordt geleverd zonder enige garantie.

De licentie geldt voor de code. De app-iconen worden gemaakt met `art/iconen/maak_iconen.py`; de twee gestileerde zeilen daarin komen uit het logo van het forum. De naam **Zeilersforum**, het logo van het forum en de inhoud van [zeilersforum.nl](https://zeilersforum.nl) zijn van het forum en vallen er niet onder.

Gebruikte bibliotheken (AndroidX, Jetpack Compose, OkHttp, Coil, kotlinx) vallen onder de Apache License 2.0, jsoup onder de MIT-licentie.
