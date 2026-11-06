# PesaFlow SMS (Android)

App ndogo ya Android inayosoma SMS za miamala (M-Pesa, Mixx by Yas, Airtel Money, HaloPesa, Selcom, benki) na kuzituma kwenye web app ya PesaFlow.

- **Pakua APK:** kwenye [Codemagic](https://codemagic.io/apps), fungua build ya mwisho ya *PesaFlow SMS (APK)* kisha pakua `PesaFlow-SMS.apk`.
- **Kujenga mwenyewe:** `./gradlew :app:assembleDebug` (inahitaji Android SDK), au fungua folder hii kwenye Android Studio.
- **Kuunganisha:** kwenye web ya PesaFlow nenda *Mipangilio → Simu za Android → Tengeneza pairing code*, kisha bandika code kwenye app.

**Faragha:** SMS za watu binafsi na matangazo hazitumwi kamwe. App haiwezi kutuma pesa wala kuhitaji PIN.

Code hii haina siri yoyote: anwani ya server na token huingia kupitia pairing code kwenye simu.
