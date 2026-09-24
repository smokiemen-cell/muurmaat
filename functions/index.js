const { onCall, HttpsError } = require('firebase-functions/v2/https');
const { defineSecret } = require('firebase-functions/params');
const admin = require('firebase-admin');

admin.initializeApp();
const resendApiKey = defineSecret('RESEND_API_KEY');

exports.requestUsernameEmail = onCall({ secrets: [resendApiKey] }, async (request) => {
  const email = String(request.data?.email || '').trim().toLowerCase();
  if (!email || !email.includes('@')) {
    throw new HttpsError('invalid-argument', 'Vul een geldig e-mailadres in.');
  }

  const snapshot = await admin.firestore()
    .collection('usernames')
    .where('email', '==', email)
    .limit(1)
    .get();

  if (snapshot.empty) {
    throw new HttpsError('not-found', 'Geen gebruikersnaam gevonden voor dit e-mailadres.');
  }

  const username = snapshot.docs[0].data().username;
  const response = await fetch('https://api.resend.com/emails', {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${resendApiKey.value()}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({
      from: 'Voegmaatje <onboarding@resend.dev>',
      to: [email],
      subject: 'Je Voegmaatje-gebruikersnaam',
      text: `Je gebruikersnaam voor Voegmaatje is: ${username}`
    })
  });

  if (!response.ok) {
    throw new HttpsError('internal', 'De e-mail kon niet worden verzonden.');
  }

  return { sent: true };
});
