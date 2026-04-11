import { HeroSection } from '../components/finans/HeroSection';
import { Categories } from '../components/finans/Categories';
import { NewsFeed } from '../components/finans/NewsFeed';
import { Sidebar } from '../components/finans/Sidebar';

export default function NewsPage() {
  return (
    <>
      <HeroSection />
      <Categories />
      <div className="grid grid-cols-1 lg:grid-cols-4 gap-8">
        <NewsFeed />
        <Sidebar />
      </div>
    </>
  );
}
