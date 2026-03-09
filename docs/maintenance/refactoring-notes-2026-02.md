# Refactoring-Notizen (2026-02)

## Fokus

- Reduzierung technischer Schulden in Build, Struktur und Testbarkeit

## Erledigt

- Maven-POM bereinigt (kaputte XML-Struktur, doppelte/defekte Dependencies entfernt)
- Abhängigkeiten auf nachvollziehbare Kernmenge reduziert
- Dokumentation konsolidiert und auf Deutsch vereinheitlicht

## Empfohlene nächste Schritte

1. Service-Grenzen weiter schärfen (insb. Plot- und Marktlogik)
2. Testabdeckung für Integrationspfade erhöhen
3. Fehlertexte und Logs standardisieren
4. Konfigurationsschema versionieren

## Risiken

- Build in isolierten Umgebungen ohne Maven-Central-Zugriff bleibt eingeschränkt
- Altdatenmigrationen können zusätzliche Tests erfordern
