import { Outlet } from 'react-router-dom';
import { Header } from './Header';
import { MarketTicker } from './MarketTicker';
import { Footer } from './Footer';
import { AIChatWidget } from './AIChatWidget';

export default function AppLayout() {
  return (
    <div className="min-h-screen bg-[#f5f6f8]">
      <Header />
      <MarketTicker />
      <main className="max-w-[1600px] mx-auto px-4 sm:px-6 lg:px-8 py-8">
        <Outlet />
      </main>
      <Footer />
      <AIChatWidget />
    </div>
  );
}
