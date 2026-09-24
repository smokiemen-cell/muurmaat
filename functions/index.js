const { onCall, HttpsError } = require('firebase-functions/v2/https');
const { defineSecret } = require('firebase-functions/params');
const { logger } = require('firebase-functions');
const admin = require('firebase-admin');

admin.initializeApp();
const serpApiKey = defineSecret('SERPAPI_KEY');

exports.getAverageGroutPrice = onCall({ secrets: [serpApiKey] }, async () => {
  const query = encodeURIComponent('voegmortel voegsel 25 kg prijs Nederland');
  const response = await fetch(`https://serpapi.com/search.json?engine=google&q=${query}&hl=nl&gl=nl&api_key=${serpApiKey.value()}`);
  if (!response.ok) throw new HttpsError('internal', 'Prijs zoeken is mislukt.');

  const data = await response.json();
  const prices = [];
  const pricePattern = /(?:EUR|€)\s?([0-9]{1,3}(?:[.,][0-9]{1,2})?)/gi;
  const products = (data.organic_results || []).filter((item) => {
    const text = JSON.stringify(item).toLowerCase();
    return /25\s*-?\s*kg|25kg/.test(text) && /voeg|mortel|voegsel/.test(text);
  });
  for (const product of products) {
    const text = JSON.stringify(product).replace(/\\u20ac/g, '€');
    let match;
    while ((match = pricePattern.exec(text)) !== null) {
      const price = Number(match[1].replace(',', '.'));
      if (price >= 2 && price <= 100) prices.push(price);
    }
  }

  const uniquePrices = [...new Set(prices)].slice(0, 10);
  logger.info('Grout price lookup', { productCount: products.length, prices: uniquePrices });
  if (!uniquePrices.length) throw new HttpsError('not-found', 'Geen prijzen gevonden.');
  const averagePrice = uniquePrices.reduce((sum, price) => sum + price, 0) / uniquePrices.length;
  return { averagePrice: Math.round(averagePrice * 100) / 100, samples: uniquePrices.length };
});
