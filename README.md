tbd libs
==========

en samling pakker som kan brukes i prosjekter

## publisering av pakker

De [publiseres til GitHub](https://github.com/orgs/navikt/packages?repo_name=tbd-libs) automatisk etter et vellykket bygg.

## lage nye moduler 

1) opprett en ny mappe, f.eks. `minmodul`
2) rediger `settings.gradle.kts` og inkluder modulen `minmodul` der
3) putt evt. avhengigheter i `minmodul/build.gradle.kts`
4) sørg for at pakkenavnet ditt følger modulnavnet, `com.navikt.tbd_libs.minmodul`
5) pakken blir publisert som `minmodul` som artifactId

# Henvendelser
Spørsmål knyttet til koden eller prosjektet kan stilles som issues her på GitHub.

## For NAV-ansatte
Interne henvendelser kan sendes via Slack i kanalen #team-bømlo-værsågod.
