# Zeilersforum-app

Android-app voor [zeilersforum.nl](https://zeilersforum.nl). Het is een nieuwe versie (3.x) die de oorspronkelijke app uit 2016–2017 vervangt.

- Kotlin, Jetpack Compose en Material 3 (Material You, donkere modus, edge-to-edge)
- compileSdk/targetSdk 37 (Android 17), minSdk 26 (Android 8)
- Pakketnaam `nl.kwillems.zeilersforum`

## Functies
- Tabbladen **Recent** (Recente discussies, periode instelbaar: sinds laatste bezoek, uren, week, maand, jaar of alles), **Forum** (categorieën en subcategorieën), **Zoeken** (hele forum) en **Mijn** (meegedaan / zelf gestart)
- Nieuw onderwerp starten vanuit een categorie
- Onderwerpen lezen met foto's, citaten, smileys (als emoji), video-links en bijlagen
- Volgorde van berichten instelbaar: **oud → nieuw** of **nieuw → oud** (knop ⇅ in een onderwerp, of via Instellingen)
- Naar een bepaalde pagina springen
- Fotoviewer met zoomen en vegen
- "Wie leest er mee?" bovenaan een onderwerp (als je bent ingelogd)
- Privéberichten (uddeIM): postvak in, verzonden, lezen, beantwoorden, nieuw bericht en verwijderen, met teller voor ongelezen berichten
- Foto's in berichten: altijd laden, alleen via wifi, of pas na een tik op "Foto bekijken"
- Instelbare ondertekening onder reacties
- Inloggen (wachtwoord versleuteld met de Android Keystore), automatisch opnieuw inloggen
- Reageren en citeren, met foto's als bijlage (worden verkleind tot max. 1920 px)
- Forumlinks openen in de app
- Updates: controleert (desgewenst dagelijks) de releases op GitHub, downloadt de nieuwe APK, controleert controlesom en sleutel en start de installatie

## Installeren
De app staat niet in de Play Store. Je installeert hem één keer zelf; daarna werkt hij zichzelf bij.

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

## Bouwen
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

De licentie geldt alleen voor de code. De naam **Zeilersforum**, het logo en app-icoon en de inhoud van [zeilersforum.nl](https://zeilersforum.nl) zijn van het forum en vallen er niet onder.

Gebruikte bibliotheken (AndroidX, Jetpack Compose, OkHttp, Coil, kotlinx) vallen onder de Apache License 2.0, jsoup onder de MIT-licentie.
