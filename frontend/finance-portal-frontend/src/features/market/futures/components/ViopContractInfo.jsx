import { useEffect, useState } from 'react';
import { Info, Calendar, Package, TrendingUp, ExternalLink, Layers, Percent, Hash, Coins } from 'lucide-react';
import { Link } from 'react-router-dom';
import { getViopContractSpec } from '../../../../api/marketApi';
import { useTranslation } from '../../../../context/LanguageContext';

// Asset class kodu → TR insan-okunur etiketi
const ASSET_CLASS_TR = {
  SINGLE_STOCK:   'Hisse Senedi',
  INDEX:          'Endeks',
  FX:             'Döviz',
  PRECIOUS_METAL: 'Kıymetli Maden',
  ENERGY:         'Enerji',
  RATE:           'Faiz',
  BOND:           'Tahvil',
};

// Sözleşme büyüklüğü açıklaması: assetClass + multiplier'a göre okunabilir bir cümle.
function contractSizeText(assetClass, multiplier, currency, t) {
  if (multiplier == null) return null;
  const m = Number(multiplier);
  switch (assetClass) {
    case 'SINGLE_STOCK': return t('1 kontrat = {n} hisse', { n: m });
    case 'INDEX':        return t('1 kontrat = {n} endeks puanı', { n: m });
    case 'FX':           return t('1 kontrat = {n} {cur}', { n: m, cur: currency || 'USD' });
    case 'PRECIOUS_METAL':
      if (m === 1) return t('1 kontrat = 1 gram');
      return t('1 kontrat = {n} ons/birim', { n: m });
    case 'ENERGY':       return t('1 kontrat = {n} MWh', { n: m });
    case 'RATE':         return t('1 kontrat = nominal {n} TL', { n: m });
    case 'BOND':         return t('1 kontrat = nominal {n} TL', { n: m });
    default:             return t('Çarpan: {n}', { n: m });
  }
}

export default function ViopContractInfo({ contract }) {
  const { t } = useTranslation();
  const [spec, setSpec] = useState(null);

  // Spec'i (multiplier, marginRate, F_ kodu, asset class) backend'den çek.
  // Bulunamazsa fallback dönecek ama found=false flag'iyle, UI'da "bilgi yok" göstermeyiz.
  useEffect(() => {
    if (!contract?.name) { setSpec(null); return undefined; }
    let cancelled = false;
    getViopContractSpec(contract.name)
      .then(s => { if (!cancelled) setSpec(s); })
      .catch(() => { if (!cancelled) setSpec(null); });
    return () => { cancelled = true; };
  }, [contract?.name]);

  if (!contract) return null;

  // Kontrat adından vade tarihini çıkarmaya çalış
  // Örnek: "AEFES Vadeli 25 May 26" → "25 Mayıs 2026"
  const extractMaturityDate = (name) => {
    if (!name) return null;
    
    const monthMap = {
      'Oca': 'Ocak', 'Şub': 'Şubat', 'Mar': 'Mart', 'Nis': 'Nisan',
      'May': 'Mayıs', 'Haz': 'Haziran', 'Tem': 'Temmuz', 'Ağu': 'Ağustos',
      'Eyl': 'Eylül', 'Eki': 'Ekim', 'Kas': 'Kasım', 'Ara': 'Aralık'
    };

    // "25 May 26" formatını ara
    const match = name.match(/(\d{1,2})\s+([A-Za-zğüşıöçĞÜŞİÖÇ]{3})\s+(\d{2})/);
    if (match) {
      const day = match[1];
      const monthShort = match[2];
      const yearShort = match[3];
      const monthFull = monthMap[monthShort] || monthShort;
      const yearFull = `20${yearShort}`;
      return `${day} ${monthFull} ${yearFull}`;
    }
    
    return null;
  };

  const maturityDate = extractMaturityDate(contract.name);

  // Şirket kodunu çıkar
  const extractCompanyCode = (name) => {
    if (!name) return null;
    const match = name.match(/^([A-Z]+)/);
    return match ? match[1] : null;
  };

  const companyCode = extractCompanyCode(contract.name);

  // Sözleşme tipini belirle
  const extractContractType = (name) => {
    if (!name) return t('Vadeli İşlem');
    if (name.includes('FİZ') || name.includes('Fiziki')) return t('Vadeli Fiziki Teslimat');
    if (name.includes('NAK') || name.includes('Nakdi')) return t('Vadeli Nakdi Uzlaşma');
    return t('Vadeli İşlem');
  };

  const contractType = extractContractType(contract.name);

  return (
    <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-6">
      <h2 className="font-bold text-gray-900 mb-4 flex items-center gap-2">
        <Info className="w-5 h-5 text-[#093eaa]" />
        {t('Sözleşme Bilgileri')}
      </h2>

      <div className="space-y-3">
        {/* Sözleşme Adı */}
        <div className="flex items-start gap-3 p-3 bg-gray-50 rounded-xl">
          <Package className="w-4 h-4 text-gray-400 flex-shrink-0 mt-0.5" />
          <div className="flex-1 min-w-0">
            <p className="text-xs text-gray-500 font-semibold mb-0.5">{t('Sözleşme Adı')}</p>
            <p className="text-sm font-bold text-gray-900 break-words">{contract.name}</p>
          </div>
        </div>

        {/* Dayanak Varlık */}
        {companyCode && (
          <Link
            to={`/market/stocks/${companyCode}`}
            className="flex items-start gap-3 p-3 bg-[#093eaa]/[0.06] rounded-xl border border-[#093eaa]/20 hover:border-[#093eaa]/40 transition-all group"
          >
            <TrendingUp className="w-4 h-4 text-[#093eaa] flex-shrink-0 mt-0.5 group-hover:scale-110 transition-transform" />
            <div className="flex-1">
              <p className="text-xs text-[#093eaa] font-semibold mb-0.5">{t('Dayanak Varlık')}</p>
              <div className="flex items-center gap-2">
                <p className="text-sm font-bold text-[#093eaa]">{companyCode} {t('Hisse Senedi')}</p>
                <ExternalLink className="w-3.5 h-3.5 text-[#093eaa] opacity-0 group-hover:opacity-100 transition-opacity" />
              </div>
              <p className="text-xs text-[#093eaa]/80 mt-1">{t('Hisse detayına git →')}</p>
            </div>
          </Link>
        )}

        {/* Vade Tarihi */}
        {maturityDate && (
          <div className="flex items-start gap-3 p-3 bg-gray-50 rounded-xl">
            <Calendar className="w-4 h-4 text-gray-400 flex-shrink-0 mt-0.5" />
            <div className="flex-1">
              <p className="text-xs text-gray-500 font-semibold mb-0.5">{t('Vade Tarihi')}</p>
              <p className="text-sm font-bold text-gray-900">{maturityDate}</p>
            </div>
          </div>
        )}

        {/* Sözleşme Tipi */}
        <div className="flex items-start gap-3 p-3 bg-gray-50 rounded-xl">
          <div className="w-4 h-4 flex items-center justify-center text-gray-400 flex-shrink-0 mt-0.5">
            <span className="text-xs font-bold">⚡</span>
          </div>
          <div className="flex-1">
            <p className="text-xs text-gray-500 font-semibold mb-0.5">{t('Sözleşme Tipi')}</p>
            <p className="text-sm font-bold text-gray-900">{contractType}</p>
          </div>
        </div>

        {/* Piyasa */}
        <div className="flex items-start gap-3 p-3 bg-gray-50 rounded-xl">
          <div className="w-4 h-4 flex items-center justify-center text-gray-400 flex-shrink-0 mt-0.5">
            <span className="text-xs font-bold">🏛</span>
          </div>
          <div className="flex-1">
            <p className="text-xs text-gray-500 font-semibold mb-0.5">{t('Piyasa')}</p>
            <p className="text-sm font-bold text-gray-900">VİOP</p>
          </div>
        </div>

        {/* ── VİOP Spec (multiplier + marginRate + F_ kod) ── */}
        {spec && spec.found && (
          <div className="mt-1 pt-3 border-t border-gray-100 space-y-3">
            <p className="text-[10px] uppercase tracking-wider font-bold text-[#093eaa]">
              {t('Sözleşme Spesifikasyonu')}
            </p>

            {/* Sözleşme büyüklüğü (multiplier) */}
            {spec.multiplier != null && (
              <div className="flex items-start gap-3 p-3 bg-amber-50/60 rounded-xl border border-amber-200/60">
                <Layers className="w-4 h-4 text-amber-700 flex-shrink-0 mt-0.5" />
                <div className="flex-1">
                  <p className="text-xs text-amber-800 font-semibold mb-0.5">{t('Sözleşme Büyüklüğü')}</p>
                  <p className="text-sm font-bold text-gray-900 tabular-nums">
                    {contractSizeText(spec.assetClass, spec.multiplier, spec.currency, t)}
                  </p>
                  <p className="text-[11px] text-amber-700/80 mt-0.5">
                    {t('Çarpan')}: <span className="font-bold tabular-nums">{Number(spec.multiplier)}</span>
                  </p>
                </div>
              </div>
            )}

            {/* Başlangıç teminat oranı */}
            {spec.marginRate != null && (
              <div className="flex items-start gap-3 p-3 bg-emerald-50/60 rounded-xl border border-emerald-200/60">
                <Percent className="w-4 h-4 text-emerald-700 flex-shrink-0 mt-0.5" />
                <div className="flex-1">
                  <p className="text-xs text-emerald-800 font-semibold mb-0.5">{t('Başlangıç Teminatı Oranı')}</p>
                  <p className="text-sm font-bold text-gray-900 tabular-nums">
                    %{(Number(spec.marginRate) * 100).toFixed(1)}
                  </p>
                  <p className="text-[11px] text-emerald-700/80 mt-0.5">
                    {t('Yaklaşık {x}x kaldıraç', { x: (1 / Number(spec.marginRate)).toFixed(1) })}
                  </p>
                </div>
              </div>
            )}

            {/* Dayanak varlık sınıfı + uzlaşma tipi */}
            <div className="grid grid-cols-2 gap-2">
              {spec.assetClass && (
                <div className="p-2.5 bg-gray-50 rounded-xl">
                  <p className="text-[10px] text-gray-500 font-semibold uppercase mb-0.5">{t('Tür')}</p>
                  <p className="text-xs font-bold text-gray-800">
                    {t(ASSET_CLASS_TR[spec.assetClass] || spec.assetClass)}
                  </p>
                </div>
              )}
              {spec.settlementType && (
                <div className="p-2.5 bg-gray-50 rounded-xl">
                  <p className="text-[10px] text-gray-500 font-semibold uppercase mb-0.5">{t('Uzlaşma')}</p>
                  <p className="text-xs font-bold text-gray-800">
                    {spec.settlementType === 'CASH' ? t('Nakdi') : t('Fiziki')}
                  </p>
                </div>
              )}
            </div>

            {/* İş Yatırım / BIST kod */}
            {spec.isYatirimCode && (
              <div className="flex items-start gap-3 p-3 bg-[#093eaa]/[0.05] rounded-xl border border-[#093eaa]/20">
                <Hash className="w-4 h-4 text-[#093eaa] flex-shrink-0 mt-0.5" />
                <div className="flex-1 min-w-0">
                  <p className="text-xs text-[#093eaa] font-semibold mb-0.5">{t('BIST / İş Yatırım Kodu')}</p>
                  <p className="text-sm font-mono font-bold text-[#093eaa] break-all">{spec.isYatirimCode}</p>
                  <p className="text-[11px] text-[#093eaa]/70 mt-0.5">{t('Resmi VİOP sözleşme kodu')}</p>
                </div>
              </div>
            )}
          </div>
        )}

        {/* Spec bulunamadıysa kısa uyarı */}
        {spec && !spec.found && (
          <div className="mt-1 p-3 bg-amber-50 border border-amber-200 rounded-xl">
            <p className="text-[11px] text-amber-800 leading-snug">
              <Coins className="inline w-3 h-3 mr-1 -mt-0.5" />
              {t('Bu sözleşme için kontrat büyüklüğü/marjin bilgisi henüz tanımlı değil. Portföye eklerken yaklaşık değerler (çarpan=1, marjin=%15) kullanılır.')}
            </p>
          </div>
        )}

        {/* Bilgi notu */}
        <div className="bg-gray-50 rounded-xl p-3 border border-gray-100">
          <p className="text-xs text-gray-600 leading-relaxed">
            <span className="font-semibold">{t('VİOP:')}</span> {t('Borsa İstanbul bünyesinde işlem gören vadeli işlem sözleşmeleridir. Yatırımcılar gelecekteki fiyat hareketlerine göre pozisyon alabilirler.')}
          </p>
        </div>
      </div>
    </div>
  );
}
