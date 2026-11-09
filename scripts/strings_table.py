"""The string table read by gen_strings.py: key -> (English, Spanish, Valencian).

Edit here, then run `python3 scripts/gen_strings.py`. Never edit the XML.
"""

S = {
"app_name": ("MeshConnect", "", ""),
"app_name_full": ("MeshConnect", "", ""),

# Common
"back": ("Back", "Volver", "Tornar"),
"cancel": ("Cancel", "Cancelar", "Cancel·lar"),
"close": ("Close", "Cerrar", "Tancar"),
"ok": ("OK", "Aceptar", "D'acord"),
"more_options": ("More options", "Más opciones", "Més opcions"),
"search": ("Search", "Buscar", "Cercar"),
"clear": ("Clear", "Borrar", "Esborrar"),
"copy": ("Copy", "Copiar", "Copiar"),
"copied": ("Copied", "Copiado", "Copiat"),
"share": ("Share", "Compartir", "Compartir"),
"send": ("Send", "Enviar", "Enviar"),
"join": ("Join", "Unirse", "Unir-se"),
"add": ("Add", "Añadir", "Afegir"),
"delete": ("Delete", "Eliminar", "Eliminar"),
"open": ("Open", "Abrir", "Obrir"),

# About
"about_title": ("About", "Acerca de", "Quant a"),
"about_version": ("Version %1$s", "Versión %1$s", "Versió %1$s"),
"about_description": (
    "Off-grid encrypted LoRa messaging for MeshCore radios. No servers, no accounts, no Google Play Services, no analytics, no crash reporting.",
    "Mensajería LoRa cifrada sin cobertura para radios MeshCore. Sin servidores, sin cuentas, sin servicios de Google Play, sin analíticas, sin informes de fallos.",
    "Missatgeria LoRa xifrada sense cobertura per a ràdios MeshCore. Sense servidors, sense comptes, sense serveis de Google Play, sense analítiques, sense informes d'errors.",
),
"about_source": ("Source code", "Código fuente", "Codi font"),
"about_issues": ("Report a problem", "Informar de un problema", "Informar d'un problema"),
"about_whats_new": ("What's new", "Novedades", "Novetats"),
"about_based_on": ("Based on", "Basado en", "Basat en"),
"about_credit_mch": (
    "MeshConnect is a fork of MeshCore Hardened. Its protocol layer, engine, storage and most screens are their work.",
    "MeshConnect es un fork de MeshCore Hardened. La capa de protocolo, el motor, el almacenamiento y la mayoría de pantallas son obra suya.",
    "MeshConnect és un fork de MeshCore Hardened. La capa de protocol, el motor, l'emmagatzematge i la majoria de pantalles són obra seua.",
),
"about_credit_reticulum": (
    "Transports, foreground service and app scaffolding, by the same author",
    "Transportes, servicio en primer plano y estructura de la app, del mismo autor",
    "Transports, servei en primer pla i estructura de l'aplicació, del mateix autor",
),
"about_credit_meshcore": (
    "The mesh firmware and protocol this app speaks",
    "El firmware y el protocolo de malla que habla esta app",
    "El firmware i el protocol de malla que parla esta aplicació",
),
"about_privacy": ("Privacy and security", "Privacidad y seguridad", "Privadesa i seguretat"),
"about_privacy_1": (
    "Map tiles and firmware downloads are the only internet connections, and both can be avoided.",
    "Los mapas y las descargas de firmware son las únicas conexiones a internet, y ambas se pueden evitar.",
    "Els mapes i les descàrregues de firmware són les úniques connexions a internet, i totes dues es poden evitar.",
),
"about_privacy_2": (
    "Messages, contacts and keys live on this phone and the radio. Nothing is uploaded.",
    "Los mensajes, contactos y claves viven en este teléfono y en la radio. No se sube nada.",
    "Els missatges, contactes i claus viuen en este telèfon i en la ràdio. No es puja res.",
),
"about_privacy_3": (
    "Channels are obfuscated, not secure: AES-ECB with a 2-byte MAC, and the key never changes.",
    "Los canales están ofuscados, no son seguros: AES-ECB con un MAC de 2 bytes, y la clave nunca cambia.",
    "Els canals estan ofuscats, no són segurs: AES-ECB amb un MAC de 2 bytes, i la clau no canvia mai.",
),
"about_privacy_4": (
    "Advert signatures are verified before a contact is imported.",
    "Las firmas de los anuncios se verifican antes de importar un contacto.",
    "Les signatures dels anuncis es verifiquen abans d'importar un contacte.",
),
"about_privacy_5": (
    "Secrets sit in the device keystore and the message database is encrypted.",
    "Los secretos se guardan en el almacén de claves del dispositivo y la base de datos de mensajes está cifrada.",
    "Els secrets es guarden en el magatzem de claus del dispositiu i la base de dades de missatges està xifrada.",
),
"about_licences": ("Open-source licences", "Licencias de código abierto", "Llicències de codi obert"),
"about_endorsement": (
    "MeshConnect is an independent project. It is not affiliated with or endorsed by the MeshCore project or the MeshCore Hardened author.",
    "MeshConnect es un proyecto independiente. No está afiliado ni respaldado por el proyecto MeshCore ni por el autor de MeshCore Hardened.",
    "MeshConnect és un projecte independent. No està afiliat ni avalat pel projecte MeshCore ni per l'autor de MeshCore Hardened.",
),
}

# key -> ((one, other) en, (one, other) es, (one, other) ca)
P = {
}

A = {
}
