# Beispiele für Befehlsabläufe

## Ablauf: Plot erstellen

1. Spieler: `/plot create`
2. Plugin prüft Limit, Standort und Umgebungsregeln
3. Plot wird erstellt und gespeichert
4. Spieler wird informiert (ID, Position, nächste Schritte)

## Ablauf: Spieler vertrauen

1. Besitzer: `/plot trust <spieler>`
2. Plugin prüft Besitz und Zielspielernamen
3. Zielspieler erhält Build-Rechte
4. Aktion wird bestätigt und persistiert

## Ablauf: Plot löschen

1. Besitzer: `/plot delete [plotId]`
2. Plugin startet Bestätigungsfenster
3. Besitzer: `/plot confirm` oder `/plot cancel`
4. Bei Bestätigung wird der Plot gelöscht und freigegeben

## Fehlerfälle

- Kein Plot vorhanden: freundliche Fehlermeldung
- Keine Berechtigung: Aktion wird abgebrochen
- Persistenzfehler: Fehler protokollieren + Spieler informieren
