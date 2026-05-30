import { NAVY, ROSE, EMERALD, AMBER } from './EconomyChart';

/**
 * Türkiye Ekonomisi bülteni — sıralı konu listesi (doğrulukpayı tarzı).
 * Her konu: başlık + açıklama paragrafları + tam genişlik grafik.
 * navLabel: üstteki sticky menüde görünen kısa ad. Metinler t() ile sarılır (TR varsayılan).
 */
export const ECONOMY_TOPICS = [
  {
    key: 'tufe',
    navLabel: 'Enflasyon (TÜFE)',
    title: 'Tüketici Fiyat Endeksi ve Enflasyon',
    chartTitle: "Türkiye'de Yıllık TÜFE Enflasyonu (%)",
    chartType: 'line', color: ROSE,
    paragraphs: [
      'Enflasyon, mal ve hizmet fiyatlarının genel düzeyindeki artışı; yani paranın alım gücündeki aşınmayı ölçer. Tüketici Fiyat Endeksi (TÜFE), hanehalkının satın aldığı mal ve hizmet sepetinin fiyat değişimini izleyerek hayat pahalılığının en temel göstergesini oluşturur.',
      'Aşağıdaki grafik TÜFE\'nin yıllık değişimini (bir önceki yılın aynı ayına göre) gösterir. TÜİK her ayın ilk haftasında açıklar.',
    ],
  },
  {
    key: 'ufe',
    navLabel: 'ÜFE',
    title: 'Üretici Fiyatları (Yİ-ÜFE)',
    chartTitle: "Türkiye'de Yıllık Yİ-ÜFE (%)",
    chartType: 'line', color: ROSE,
    paragraphs: [
      'Yurt İçi Üretici Fiyat Endeksi (Yİ-ÜFE), üreticilerin yurt içinde satışa konu olan ürünlerinin fiyatlarındaki değişimi ölçer; yani maliyet tarafını gösterir.',
      'ÜFE\'deki yükseliş genellikle bir süre sonra tüketici fiyatlarına (TÜFE) yansıdığı için enflasyonun öncü göstergesi kabul edilir.',
    ],
  },
  {
    key: 'cekirdek',
    navLabel: 'Çekirdek',
    title: 'Çekirdek Enflasyon',
    chartTitle: 'Çekirdek Enflasyon — Yıllık (%)',
    chartType: 'line', color: ROSE,
    paragraphs: [
      'Çekirdek enflasyon; fiyatı mevsim, hava koşulları veya küresel piyasalara göre çok oynayan gıda, enerji, alkol-tütün ve altın gibi kalemler dışarıda bırakılarak hesaplanır.',
      'Böylece enflasyonun geçici dalgalanmalardan arındırılmış "ana eğilimi" görülür; Merkez Bankası kararlarında yakından izlenir.',
    ],
  },
  {
    key: 'abdCpi',
    navLabel: 'ABD Enflasyonu',
    title: 'ABD Enflasyonu (CPI)',
    chartTitle: 'ABD Yıllık Enflasyonu — CPI (%)',
    chartType: 'line', color: ROSE,
    paragraphs: [
      'ABD Tüketici Fiyat Endeksi (CPI), dünyanın en büyük ekonomisindeki enflasyonu ölçer ve küresel faiz beklentilerinin temel belirleyicisidir. ABD enflasyonu yükseldiğinde Fed faizleri yükseltme eğilimine girer; bu da dolar ve gelişen ülke piyasalarını doğrudan etkiler.',
      'Grafik, ABD CPI\'ın yıllık değişimini (bir önceki yılın aynı ayına göre) gösterir. Yatırımınızın "dolar bazında" reel getirisini değerlendirirken TL enflasyonu kadar dolar enflasyonu da önemlidir — çünkü dolar da zamanla alım gücü kaybeder. Kaynak: FRED (St. Louis Fed).',
    ],
  },
  {
    key: 'politikaFaizi',
    navLabel: 'Politika Faizi',
    title: 'Politika Faizi',
    chartTitle: 'Politika / Fonlama Faizi (%)',
    chartType: 'area', color: NAVY,
    paragraphs: [
      'Politika faizi, Merkez Bankası\'nın para politikasını yönlendirmek için kullandığı temel orandır; Türkiye\'de bir hafta vadeli repo ihale faizi üzerinden tanımlanır.',
      'Faiz artışları kredi maliyetlerini yükselterek talebi ve enflasyonu sınırlamayı; indirimler ise ekonomik aktiviteyi canlandırmayı hedefler. Piyasadaki diğer tüm faizlere yön verir.',
    ],
  },
  {
    key: 'mevduatFaizi',
    navLabel: 'Mevduat Faizi',
    title: 'Mevduat Faizi',
    chartTitle: 'TL Mevduat Faizi (%)',
    chartType: 'area', color: NAVY,
    paragraphs: [
      'Bankaların Türk Lirası mevduata uyguladığı ağırlıklı ortalama faiz oranıdır. Tasarruf sahibinin parasını bankada tutarak elde edeceği getiriyi gösterir.',
      'Mevduat faizinin enflasyonun üzerinde olması "reel pozitif getiri" anlamına gelir; altında kaldığında tasarruf enflasyona yenilir.',
    ],
  },
  {
    key: 'ihtiyacKredisiFaizi',
    navLabel: 'Kredi Faizi',
    title: 'İhtiyaç Kredisi Faizi',
    chartTitle: 'İhtiyaç Kredisi Faizi (%)',
    chartType: 'area', color: NAVY,
    paragraphs: [
      'Bankaların bireysel ihtiyaç kredilerine uyguladığı ağırlıklı ortalama faiz oranıdır ve hanehalkının borçlanma maliyetini doğrudan etkiler.',
      'Politika faizindeki değişimler genellikle kısa sürede kredi faizlerine yansır.',
    ],
  },
  {
    key: 'gsyihBuyume',
    navLabel: 'Büyüme',
    title: 'Ekonomik Büyüme (GSYİH)',
    chartTitle: 'GSYİH Değişimi — Çeyreklik (Yıllık %)',
    chartType: 'bar', color: NAVY,
    paragraphs: [
      'Ekonomik büyüme, bir ülkede üretilen mal ve hizmetlerin toplam değerindeki (Gayrisafi Yurt İçi Hasıla) artışı ifade eder. Reel büyüme, fiyat artışlarının etkisinden arındırıldığı için ekonomideki gerçek genişlemeyi yansıtır.',
      'Grafik, GSYİH\'nin bir önceki yılın aynı çeyreğine göre reel değişim oranını (%) gösterir.',
    ],
  },
  {
    key: 'kisiBasiGelir',
    navLabel: 'Kişi Başı Gelir',
    title: 'Kişi Başına Milli Gelir',
    chartTitle: 'Kişi Başına GSYH (ABD Doları, Yıllık)',
    chartType: 'bar', color: NAVY,
    paragraphs: [
      'Kişi başına milli gelir, toplam gelirin nüfusa bölünmesiyle elde edilir ve ülkenin uluslararası refah sıralamasındaki yerini gösterir (genellikle ABD doları cinsinden ifade edilir).',
      'Dolar bazlı olduğu için hem ekonomik büyümeden hem de döviz kurundaki değişimden etkilenir.',
    ],
  },
  {
    key: 'issizlik',
    navLabel: 'İşsizlik',
    title: 'İşsizlik Oranı',
    chartTitle: "Türkiye'de İşsizlik Oranı (%)",
    chartType: 'area', color: NAVY,
    paragraphs: [
      'İşsizlik oranı, çalışma çağındaki işgücü içinde iş arayan ve çalışmaya hazır olanların oranını gösterir; büyümenin topluma nasıl yansıdığını ölçen kritik bir sosyal göstergedir.',
      'Grafik, mevsim etkisinden arındırılmış işsizlik oranını aylar itibarıyla gösterir.',
    ],
  },
  {
    key: 'usdTry',
    navLabel: 'Dolar/TL',
    title: 'Döviz Kuru (Dolar/TL)',
    chartTitle: 'ABD Doları / Türk Lirası',
    chartType: 'line', color: NAVY,
    paragraphs: [
      'Döviz kuru, yerli paranın yabancı paralar karşısındaki değerini gösterir. Türkiye\'de en çok izlenen kur, ABD doları karşısındaki TL değeridir.',
      'İthalat, dış borç ve enerji maliyetleri büyük ölçüde dövize bağlı olduğundan kur, maliyet kanalıyla enflasyonu doğrudan etkiler; aynı zamanda yabancı yatırımcı algısının barometresidir.',
    ],
  },
  {
    key: 'eurTry',
    navLabel: 'Euro/TL',
    title: 'Döviz Kuru (Euro/TL)',
    chartTitle: 'Euro / Türk Lirası',
    chartType: 'line', color: NAVY,
    paragraphs: [
      'Euro/TL kuru, Avro Bölgesi ile yoğun ticaret yapan Türkiye için doların yanı sıra en önemli ikinci kur referansıdır.',
    ],
  },
  {
    key: 'bist100',
    navLabel: 'BIST 100',
    title: 'BIST 100 Endeksi',
    chartTitle: 'BIST 100 Endeksi (Aylık Kapanış)',
    chartType: 'area', color: EMERALD,
    paragraphs: [
      'BIST 100, Borsa İstanbul\'da işlem gören en yüksek piyasa değerine ve işlem hacmine sahip 100 şirketin performansını yansıtan ana endekstir; Türkiye hisse senedi piyasasının genel gidişatının göstergesidir.',
      'Endeks; şirket kârlılıkları, faiz oranları ve yatırımcı risk iştahıyla birlikte hareket eder ve uzun vadede enflasyona karşı bir korunma aracı olarak da izlenir. Grafik aylık kapanış seviyelerini gösterir.',
    ],
  },
  {
    key: 'gramAltin',
    navLabel: 'Gram Altın',
    title: 'Gram Altın (TL)',
    chartTitle: 'Gram Altın — TL (Aylık)',
    chartType: 'line', color: AMBER,
    paragraphs: [
      'Gram altın, Türkiye\'de en yaygın tasarruf ve enflasyondan korunma araçlarından biridir. Fiyatı hem ons altının küresel seyrine hem de dolar/TL kuruna bağlıdır.',
      'Grafik, TCMB\'nin Kapalıçarşı verilerine dayanan gram altın (TL) fiyatını aylık olarak gösterir. Anlık fiyat için Piyasalar bölümündeki Altın sayfasını kullanabilirsiniz.',
    ],
  },
  {
    key: 'cariDenge',
    navLabel: 'Cari Denge',
    title: 'Cari İşlemler Dengesi',
    chartTitle: 'Cari İşlemler Dengesi (Aylık, Milyon $)',
    chartType: 'area', color: ROSE,
    paragraphs: [
      'Cari denge, bir ülkenin dış dünyayla yaptığı mal-hizmet ticareti, gelir ve transferlerin toplam sonucudur. Dünyadan kazanılan döviz harcanandan azsa cari açık oluşur.',
      'Sürekli cari açık veren ülkeler, açığı finanse etmek için borçlanmaya veya sermaye girişlerine bağımlı hale gelir; bu da küresel koşullar değiştiğinde kırılganlık yaratır. Grafikte eksi değerler açığı ifade eder.',
    ],
  },
  {
    key: 'rezervler',
    navLabel: 'Rezervler',
    title: 'TCMB Rezervleri',
    chartTitle: 'TCMB Toplam Uluslararası Rezervler (Milyon $)',
    chartType: 'area', color: NAVY,
    paragraphs: [
      'Merkez Bankası rezervleri, TCMB\'nin kasasındaki döviz ve altın varlıklarıdır. Ülkenin dış borç ödemeleri ve olası dış şoklar karşısındaki kalkanı olarak görülür.',
      'Rezervlerin güçlü olması, kur istikrarı ve dışa karşı dayanıklılık açısından piyasalarca yakından izlenir.',
    ],
  },
  {
    key: 'reelEfektifKur',
    navLabel: 'Reel Kur',
    title: 'Reel Efektif Döviz Kuru',
    chartTitle: 'Reel Efektif Döviz Kuru (TÜFE Bazlı)',
    chartType: 'line', color: NAVY,
    paragraphs: [
      'Reel efektif döviz kuru, TL\'nin ticaret ortaklarının para birimleri karşısındaki değerini enflasyon farklarından arındırarak ölçer.',
      'Endeksin yükselmesi TL\'nin reel olarak değerlendiğini (ihracatın görece pahalılaştığını), düşmesi ise değer kaybettiğini gösterir.',
    ],
  },
  {
    key: 'butceDengesi',
    navLabel: 'Bütçe',
    title: 'Bütçe Dengesi',
    chartTitle: 'Genel Bütçe Dengesi (Aylık, Bin TL)',
    chartType: 'bar', color: ROSE,
    paragraphs: [
      'Bütçe dengesi, devletin vergi ve diğer gelirleri ile harcamaları arasındaki farktır. Giderler gelirleri aşarsa bütçe açığı oluşur.',
      'Bütçe açığının kontrol altında tutulması, mali disiplinin korunduğunun en temel göstergelerinden biridir. Grafikte eksi değerler açığı ifade eder.',
    ],
  },
  {
    key: 'kapasiteKullanim',
    navLabel: 'Kapasite',
    title: 'Kapasite Kullanım Oranı',
    chartTitle: 'İmalat Sanayi Kapasite Kullanım Oranı (%)',
    chartType: 'area', color: NAVY,
    paragraphs: [
      'Kapasite kullanım oranı, imalat sanayinde mevcut üretim kapasitesinin ne kadarının fiilen kullanıldığını gösterir.',
      'Oranın yükselmesi talebin ve üretimin canlandığına, düşmesi ise ekonomik aktivitenin yavaşladığına işaret eder; büyümenin öncü göstergelerindendir.',
    ],
  },
  {
    key: 'tuketiciGuven',
    navLabel: 'Güven',
    title: 'Tüketici Güven Endeksi',
    chartTitle: 'Tüketici Güven Endeksi',
    chartType: 'line', color: NAVY,
    paragraphs: [
      'Tüketici güven endeksi, hanehalkının mevcut ve gelecekteki ekonomik duruma ilişkin algısını ve harcama eğilimini ölçer.',
      '100\'ün altındaki değerler kötümser, üzerindeki değerler iyimser bir görünümü ifade eder; iç talebin yönü hakkında ipucu verir.',
    ],
  },
];
