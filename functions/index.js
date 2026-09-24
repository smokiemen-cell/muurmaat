const { onCall, HttpsError } = require('firebase-functions/v2/https');
const { defineSecret } = require('firebase-functions/params');
const { logger } = require('firebase-functions');
const admin = require('firebase-admin');

admin.initializeApp();
const serpApiKey = defineSecret('SERPAPI_KEY');

exports.getAverageGroutPrice = onCall({ secrets: [serpApiKey] }, async () => {
  const searchQuery = 'voegmortel 25 kg prijs';
  const query = encodeURIComponent(searchQuery);
  const response = await fetch(`https://serpapi.com/search.json?engine=google&q=${query}&location=Netherlands&hl=nl&gl=nl&num=20&api_key=${serpApiKey.value()}`);
  const responseText = await response.text();
  if (!response.ok) {
    logger.error('SerpAPI error', { status: response.status, body: responseText.slice(0, 300) });
    throw new HttpsError('internal', 'Prijs zoeken is mislukt.');
  }

  const data = JSON.parse(responseText);
  const prices = [];
  const pricePattern = /(?:EUR|€)\s?([0-9]{1,3}(?:[.,][0-9]{1,2})?)/gi;
  const resultItems = [...(data.organic_results || []), ...(data.shopping_results || [])];
  const products = resultItems.filter((item) => {
    const text = JSON.stringify(item).toLowerCase();
    return /voeg|mortel|voegsel/.test(text);
  });
  logger.info('SerpAPI results', {
    organicCount: resultItems.length,
    returnedQuery: data.search_parameters?.q,
    titles: resultItems.slice(0, 5).map((item) => item.title || item.name || '')
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
