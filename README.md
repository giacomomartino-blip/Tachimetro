# Tachimetro, nuova app

App Android da usare in auto, riscritta da zero e costruita un modulo alla volta. Questo ramo contiene il **modulo Base**, solo modalità guidatore, solo telefono.

## Cosa fa il modulo Base

1. Al primo avvio chiede due permessi: dispositivi nelle vicinanze, per il Bluetooth, e notifiche.
2. Ti fa scegliere la tua auto dall'elenco dei dispositivi Bluetooth già associati al telefono. Puoi aggiungerne altre dalle Impostazioni.
3. Si apre solo quando la apri tu. Se l'auto non è collegata mostra la schermata di attesa e si attiva da sola appena il telefono si collega.
4. Con «Prova senza auto» funziona anche senza Bluetooth, fino alla chiusura.
5. Mentre è aperta resta attiva anche con lo schermo spento, con la notifica fissa «Tachimetro attivo» e il pulsante Chiudi.
6. Quando l'auto si scollega parte un conto alla rovescia di 60 secondi, poi l'app si chiude del tutto. «Resto qui» annulla la chiusura; se l'auto si ricollega prima, il conto si annulla da solo.
7. Si chiude del tutto dal pulsante Chiudi, dalla notifica o togliendola dalle app recenti: non resta nulla in esecuzione.
8. La pagina Stato dice se Bluetooth, permessi, auto e notifica sono a posto.

## Installazione

1. Disinstalla la vecchia Tachimetro: la nuova ha lo stesso nome ma è un'app diversa.
2. Scarica l'ultimo file `Tachimetro-nuova-N.apk` dalla sezione Releases e aprilo dal telefono.
3. Se Play Protect o Auto Blocker di Samsung bloccano l'installazione, consenti l'installazione da questa fonte.

## Come si compila

GitHub Actions compila l'APK firmato a ogni modifica di questo ramo, dopo aver eseguito le prove automatiche delle regole. Servono questi segreti del repository: KEYSTORE_BASE64, KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD.

Il carattere Inter e il carattere Space Grotesk sono distribuiti con licenza SIL Open Font License 1.1.
