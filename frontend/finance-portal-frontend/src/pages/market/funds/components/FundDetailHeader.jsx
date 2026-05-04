import { Link } from 'react-router-dom';
import { ArrowLeft, BarChart2 } from 'lucide-react';

export default function FundDetailHeader({ fund, code }) {
    const isPos = fund?.changePercent && !fund.changePercent.startsWith('%-');

    return (
        <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-6">
            <div className="flex items-start justify-between gap-4 flex-wrap">
                <div className="flex items-start gap-4">
                    <Link to="/market/tefas" className="mt-1 text-gray-400 hover:text-[#093eaa] transition-colors flex-shrink-0">
                        <ArrowLeft className="w-5 h-5" />
                    </Link>
                    <div className="flex items-start gap-3">
                        {fund?.logoUrl && (
                            <img
                                src={fund.logoUrl}
                                alt={fund.code}
                                className="w-12 h-12 rounded-xl object-contain border border-gray-100 p-1 bg-white shadow-sm flex-shrink-0"
                                onError={e => { e.target.style.display = 'none'; }}
                            />
                        )}
                        <div>
                            <div className="flex items-center gap-2 flex-wrap">
                                <span className="text-2xl font-black text-gray-900 font-mono">{fund?.code ?? code}</span>
                                {fund?.date && (
                                    <span className="text-xs text-gray-400 bg-gray-100 px-2 py-0.5 rounded-full">
                    {fund.date}
                  </span>
                                )}
                            </div>
                            <p className="text-sm text-gray-600 mt-0.5 max-w-lg">{fund?.title ?? '-'}</p>
                            {fund?.lastPrice && (
                                <div className="flex items-baseline gap-3 mt-2">
                                    <span className="text-3xl font-black text-gray-900">{fund.lastPrice} TL</span>
                                    {fund.changeAmount && fund.changePercent && (
                                        <span className={`text-sm font-bold ${isPos ? 'text-emerald-600' : 'text-rose-600'}`}>
                      {fund.changeAmount} ({fund.changePercent})
                    </span>
                                    )}
                                </div>
                            )}
                        </div>
                    </div>
                </div>
                <Link
                    to={`/market/tefas/compare?codes=${code}`}
                    className="inline-flex items-center gap-2 px-4 py-2 rounded-xl text-sm font-semibold border border-[#093eaa] text-[#093eaa] bg-white hover:bg-blue-50 transition-all flex-shrink-0"
                >
                    <BarChart2 className="w-4 h-4" />
                    Karşılaştır
                </Link>
            </div>
        </div>
    );
}
