import common from './translations/common';
import layout from './translations/layout';
import header from './translations/header';
import footer from './translations/footer';
import home from './translations/home';
import auth from './translations/auth';
import profile from './translations/profile';
import dashboard from './translations/dashboard';
import news from './translations/news';
import market from './translations/market';
import portfolio from './translations/portfolio';
import admin from './translations/admin';
import extra from './translations/extra';

const merge = (...dicts) =>
  Object.assign({}, ...dicts.map((d) => (d && d.en) || {}));

const translations = {
  en: merge(common, layout, header, footer, home, auth, profile, dashboard, news, market, portfolio, admin, extra),
};

export default translations;
