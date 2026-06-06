const en = {
  // ── AiAnalysisPage ──────────────────────────────────────────────
  'AI Portföy Analizi': 'AI Portfolio Analysis',
  'pozisyon': 'positions',
  'nominal': 'nominal',
  'Analiz alınamadı.': 'Could not load analysis.',
  'Portföyün AI ile analiz ediliyor…': 'Analyzing your portfolio with AI…',
  'Metrikler hesaplanıyor, AI yorumu hazırlanıyor.': 'Metrics are being computed, the AI commentary is being prepared.',
  'Portföye dön': 'Back to portfolio',

  // Score cards
  'Risk Skoru': 'Risk Score',
  'risk seviyesi': 'risk level',
  '0–100 · yüksek = daha riskli.': '0–100 · higher = riskier.',
  'Volatilite, yoğunlaşma, varlık tipi ve maksimum düşüşten hesaplanır. Aşağıdaki çubuklar her etkenin skora katkısıdır.':
    'Computed from volatility, concentration, asset type and maximum drawdown. The bars below show each factor\'s contribution to the score.',
  'Sağlık Skoru': 'Health Score',
  'portföy sağlığı': 'portfolio health',
  '0–100 · yüksek = daha sağlıklı.': '0–100 · higher = healthier.',
  'Çeşitlendirme, reel getiri, risk-ayarlı getiri (Sharpe) ve düşüş kontrolünden hesaplanır. Çubuklar her etkenin katkısını gösterir.':
    'Computed from diversification, real return, risk-adjusted return (Sharpe) and drawdown control. The bars show each factor\'s contribution.',
  'Portföy Kimliği': 'Portfolio Identity',
  'Büyüme-odaklı': 'Growth-oriented',
  'Korumacı': 'Conservative',
  'Hisse/kripto/vadeli/emtia = büyüme; tahvil/altın/döviz = korumacı; fon = karma.':
    'Stocks/crypto/futures/commodities = growth; bonds/gold/FX = defensive; funds = mixed.',
  'Sınıflandırma için veri yok.': 'No data for classification.',

  // Risk-adjusted metrics
  'Risk-Ayarlı Metrikler': 'Risk-Adjusted Metrics',
  'Yıllık Getiri': 'Annual Return',
  'Yıllıklandırılmış': 'Annualized',
  'Yıllık Volatilite': 'Annual Volatility',
  'Dalgalanma': 'Fluctuation',
  'Sharpe': 'Sharpe',
  'Birim risk başına getiri': 'Return per unit of risk',
  'Sortino': 'Sortino',
  'Aşağı-yön risk-ayarlı': 'Downside risk-adjusted',
  'Max Düşüş': 'Max Drawdown',
  'Tepe→dip kayıp': 'Peak-to-trough loss',
  'Beta (BIST100)': 'Beta (BIST100)',
  'Piyasaya duyarlılık': 'Market sensitivity',
  'Risk metrikleri için geçmiş çok kısa': 'History too short for risk metrics',
  'yetersiz veri': 'insufficient data',

  // Concentration
  'Yoğunlaşma Riski': 'Concentration Risk',
  'en büyük pozisyon': 'largest position',
  'İlk 3 pozisyon': 'Top 3 positions',
  'Herfindahl': 'Herfindahl',
  'Yüksek yoğunlaşma': 'High concentration',
  'Orta yoğunlaşma': 'Medium concentration',
  'İyi çeşitlendirilmiş': 'Well diversified',

  // Monte Carlo
  'Monte Carlo Projeksiyon — Olası değer aralığı': 'Monte Carlo Projection — Probable value range',
  '{n} ay medyan': '{n}-month median',
  'getiri {x}': 'return {x}',
  'İyimser (%95)': 'Optimistic (95%)',
  'üst senaryo': 'upper scenario',
  'Kötümser (%5)': 'Pessimistic (5%)',
  'alt senaryo': 'lower scenario',
  'Kayıp olasılığı': 'Loss probability',
  '%5–%95 aralığı': '5%–95% range',
  'Medyan': 'Median',
  'ay': 'mo',

  // Stress tests
  'Tarihsel Stres Testleri — Mevcut portföyün kriz dayanıklılığı':
    'Historical Stress Tests — Current portfolio\'s crisis resilience',
  'Mevcut varlık dağılımın o dönemde nasıl etkilenirdi (varlık-tipi proxy\'leriyle tahmin). Pozitif = kazanç (ör. kur krizinde döviz/altın ağırlığı yükselir).':
    'How your current asset allocation would have been affected in that period (estimated with asset-type proxies). Positive = gain (e.g. in a currency crisis, FX/gold weight rises).',

  // Asset signals
  'Varlık Bazlı Sinyaller — Her pozisyonun teknik durumu': 'Asset-Based Signals — Technical status of each position',
  'ağırlık': 'weight',
  'Getiri': 'Return',
  'Trend = fiyatın MA20/MA50\'ye göre konumu; çubuk = 52-hafta bandındaki yer; momentum = 1/3 aylık değişim.':
    'Trend = price position relative to MA20/MA50; bar = position within the 52-week range; momentum = 1/3-month change.',
  '52h verisi yok': 'No 52w data',
  '52h dip': '52w low',
  'tepe': 'high',
  'Fiyat MA20 ve MA50 üstünde': 'Price above MA20 and MA50',
  'Fiyat MA20 ve MA50 altında': 'Price below MA20 and MA50',
  'MA50 üstü, MA20 altı': 'Above MA50, below MA20',
  'MA20 üstü, MA50 altı': 'Above MA20, below MA50',

  // AI report
  'AI Yorum Raporu': 'AI Commentary Report',
  'AI yorumu şu an kullanılamıyor (model meşgul/kota). Yukarıdaki tüm metrikler ve grafikler geçerlidir.':
    'AI commentary is currently unavailable (model busy/quota). All metrics and charts above remain valid.',
  'Portföyünüzde işlem/pozisyon bulunamadı. Analiz için önce alım-satım işlemi ekleyin.':
    'No transactions/positions found in your portfolio. Add a buy/sell transaction first to run the analysis.',

  // ── RebalanceCard ───────────────────────────────────────────────
  'Risk Profili Testi': 'Risk Profile Test',
  'Yatırım vadem genelde…': 'My investment horizon is usually…',
  '1 yıldan kısa': 'Less than 1 year',
  '1–5 yıl': '1–5 years',
  '5 yıldan uzun': 'More than 5 years',
  'Portföyüm bir ayda %20 düşerse…': 'If my portfolio drops 20% in a month…',
  'Satar, zararı durdururum': 'I sell and stop the loss',
  'Bekler, izlerim': 'I wait and watch',
  'Fırsat görür, eklerim': 'I see an opportunity and add',
  'Önceliğim…': 'My priority is…',
  'Sermayeyi korumak': 'Preserving capital',
  'Dengeli büyüme': 'Balanced growth',
  'Maksimum getiri': 'Maximum return',
  'Yatırım deneyimim…': 'My investment experience…',
  'Yeni başlıyorum': 'Just starting out',
  'Orta düzey': 'Intermediate',
  'Deneyimli': 'Experienced',
  'Fiyat dalgalanmasına tahammülüm…': 'My tolerance for price swings…',
  'Düşük': 'Low',
  'Orta': 'Medium',
  'Yüksek': 'High',
  'Profilimi belirle': 'Determine my profile',
  'Yeniden Dengeleme': 'Rebalancing',
  'hedef': 'target',
  'Hesaplanıyor…': 'Calculating…',
  'Risk profilimi ölç': 'Measure my risk profile',
  'Toplam sapma': 'Total drift',
  'mevcut dağılımın seçili profile uzaklığı (0 = birebir).': 'distance of the current allocation from the selected profile (0 = exact match).',
  'mevcut': 'current',

  // ── ForecastCard ────────────────────────────────────────────────
  'Gelecek Projeksiyon — Olası değer (Monte Carlo)': 'Future Projection — Probable value (Monte Carlo)',
  'Geçmiş getiri/oynaklığın süreceği varsayımıyla olası aralık; kesin tahmin değil. Yön yorumu AI raporunda.':
    'Probable range assuming past return/volatility persists; not a precise forecast. Directional commentary is in the AI report.',
  'medyan': 'median',
  'aralık': 'range',
  'kayıp olasılığı': 'loss probability',
  'Varlık': 'Asset',
  'Trend': 'Trend',
  'Varlık değerleri medyan (en olası) getiri tahminidir; aşırı geçmiş getiriler ortalamaya dönüş için yumuşatılmıştır. Trend = fiyatın MA20/MA50\'ye göre yönü.':
    'Asset values are median (most likely) return estimates; extreme past returns are smoothed toward mean reversion. Trend = price direction relative to MA20/MA50.',

  // ── Shared / dynamic labels (fixed small set from backend) ──────
  '1 Ay': '1 Month',
  '3 Ay': '3 Months',
  '1 Yıl': '1 Year',
  'Agresif': 'Aggressive',
  'Dengeli': 'Balanced',
  'Sağlıklı': 'Healthy',
  'Zayıf': 'Weak',
  'ARTIR': 'INCREASE',
  'AZALT': 'REDUCE',
  'KORU': 'KEEP',
  'Yükseliş eğilimi': 'Uptrend',
  'Düşüş eğilimi': 'Downtrend',
  'Yatay/belirsiz': 'Flat/unclear',
  'Kararsız': 'Mixed',

  // ── Risk/Health score factor bar labels (from backend) ──────────
  'Volatilite': 'Volatility',
  'Yoğunlaşma': 'Concentration',
  'Varlık tipi': 'Asset type',
  'Max düşüş': 'Max drawdown',
  'Geçmiş kısa': 'Short history',
  'Çeşitlendirme': 'Diversification',
  'Risk-ayarlı getiri': 'Risk-adjusted return',
  'Reel getiri': 'Real return',
  'Düşüş kontrolü': 'Drawdown control',

  // ── Factor bar tooltips (detail, from backend) ──────────────────
  'Yıllık dalgalanma katkısı': 'Annual volatility contribution',
  'En büyük pozisyon + Herfindahl': 'Largest position + Herfindahl',
  'Kripto/vadeli yüksek, tahvil/mevduat düşük': 'Crypto/futures high, bonds/deposits low',
  'Tepe-dip kayıp katkısı': 'Peak-to-trough loss contribution',
  'Volatilite/drawdown hesaplanamadı': 'Volatility/drawdown could not be computed',
  'Yoğunlaşma ne kadar düşükse o kadar iyi': 'The lower the concentration, the better',
  'Sharpe oranı': 'Sharpe ratio',
  'Enflasyondan arındırılmış getiri': 'Inflation-adjusted return',
  'Max drawdown ne kadar küçükse o kadar iyi': 'The smaller the max drawdown, the better',

  // ── Portfolio Identity detail text (from backend) ───────────────
  'Büyüme-odaklı varlık ağırlığı baskın; getiri potansiyeli yüksek, dalgalanma da yüksek.':
    'Growth-oriented asset weight dominates; high return potential, but also high volatility.',
  'Korumacı/sabit-getirili ağırlık baskın; sermaye koruma önceliği, getiri daha ılımlı.':
    'Defensive/fixed-income weight dominates; capital preservation is the priority, returns are more moderate.',
  'Büyüme ve korumacı varlıklar dengeli; orta risk-getiri profili.':
    'Growth and defensive assets are balanced; a moderate risk-return profile.',

  // ── AI GridBoard widget subtitles (portfolio charts section) ────
  'Monte Carlo Projeksiyon': 'Monte Carlo Projection',
  '0–100 · yüksek = daha riskli': '0–100 · higher = riskier',
  '0–100 · yüksek = daha sağlıklı': '0–100 · higher = healthier',
  'Varlık-tipi ağırlığına göre profil': 'Profile by asset-type weight',
  'Yeterli geçmiş yok': 'Not enough history',
  '1 yıllık olası değer aralığı (kesin tahmin değil)': '1-year possible value range (not a precise forecast)',
};

export default { en };
