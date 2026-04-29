#!/bin/bash
# ═══════════════════════════════════════════════════════════════════
# GÉNÉRATION D'UN CERTIFICAT SSL AUTO-SIGNÉ (DÉVELOPPEMENT UNIQUEMENT)
# ═══════════════════════════════════════════════════════════════════
#
# ⚠️ ATTENTION : Ce certificat est AUTO-SIGNÉ.
# Il NE doit PAS être utilisé en production !
# Le navigateur affichera un avertissement de sécurité.
#
# Pour la production, utiliser Let's Encrypt (certbot) :
#   sudo apt install certbot
#   sudo certbot certonly --standalone -d tasksphere.example.com
#
# UTILISATION :
#   bash nginx/ssl/generate-cert.sh
#
# Les fichiers générés :
#   nginx/ssl/tasksphere.crt  → Certificat public
#   nginx/ssl/tasksphere.key  → Clé privée (NE JAMAIS partager !)

# Créer le répertoire s'il n'existe pas
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
mkdir -p "$SCRIPT_DIR"

echo "🔑 Génération du certificat SSL auto-signé pour le développement..."
echo "   Répertoire : $SCRIPT_DIR"
echo ""

# Génération du certificat avec OpenSSL
# -x509 : certificat auto-signé (pas de CSR)
# -nodes : pas de mot de passe sur la clé privée (sinon nginx le demande au démarrage)
# -days 365 : valide 1 an
# -newkey rsa:2048 : clé RSA 2048 bits (suffisant pour le dev)
openssl req -x509 -nodes -days 365 -newkey rsa:2048 \
  -keyout "$SCRIPT_DIR/tasksphere.key" \
  -out "$SCRIPT_DIR/tasksphere.crt" \
  -subj "/C=MA/ST=Casablanca/L=Casablanca/O=TaskSphere/OU=Dev/CN=localhost" \
  -addext "subjectAltName=DNS:localhost,IP:127.0.0.1"

# Vérification
if [ -f "$SCRIPT_DIR/tasksphere.crt" ] && [ -f "$SCRIPT_DIR/tasksphere.key" ]; then
    echo "✅ Certificat généré avec succès !"
    echo "   📄 Certificat : $SCRIPT_DIR/tasksphere.crt"
    echo "   🔑 Clé privée : $SCRIPT_DIR/tasksphere.key"
    echo ""
    echo "📋 Pour activer HTTPS :"
    echo "   1. Décommenter le service 'nginx' dans docker-compose.yml"
    echo "   2. docker compose up -d"
    echo "   3. Accéder à https://localhost"
    echo ""
    echo "⚠️  Le navigateur affichera un avertissement (certificat auto-signé)."
    echo "   Cliquer sur 'Avancé' → 'Continuer vers localhost (non sécurisé)'"
else
    echo "❌ Erreur lors de la génération du certificat."
    echo "   Vérifiez que openssl est installé : sudo apt install openssl"
    exit 1
fi