import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import Pagination from '../Pagination';
import { LanguageProvider } from '../../../context/LanguageContext';

// Pagination, useTranslation() hook'unu kullanır → LanguageProvider ile sarmalanmalı.
// Varsayılan dil "tr" olduğundan t(key) anahtarın kendisini döndürür.
function renderWithLang(ui) {
  return render(<LanguageProvider>{ui}</LanguageProvider>);
}

describe('Pagination (jsdom + testing-library + React 19 pipeline)', () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it('tek sayfa ve sizer yoksa hiçbir şey render etmez (null)', () => {
    const { container } = renderWithLang(
      <Pagination page={0} totalPages={1} onChange={() => {}} />
    );
    expect(container).toBeEmptyDOMElement();
  });

  it('çok sayfalı durumda sayfa numaralarını (1-tabanlı) render eder', () => {
    renderWithLang(<Pagination page={0} totalPages={3} onChange={() => {}} />);
    expect(screen.getByText('1')).toBeInTheDocument();
    expect(screen.getByText('2')).toBeInTheDocument();
    expect(screen.getByText('3')).toBeInTheDocument();
  });

  it('navigasyon ok butonlarını aria-label ile render eder', () => {
    renderWithLang(<Pagination page={1} totalPages={5} onChange={() => {}} />);
    expect(screen.getByLabelText('İlk sayfa')).toBeInTheDocument();
    expect(screen.getByLabelText('Önceki sayfa')).toBeInTheDocument();
    expect(screen.getByLabelText('Sonraki sayfa')).toBeInTheDocument();
    expect(screen.getByLabelText('Son sayfa')).toBeInTheDocument();
  });

  it('ilk sayfada "İlk"/"Önceki" butonları disabled olur', () => {
    renderWithLang(<Pagination page={0} totalPages={5} onChange={() => {}} />);
    expect(screen.getByLabelText('İlk sayfa')).toBeDisabled();
    expect(screen.getByLabelText('Önceki sayfa')).toBeDisabled();
    expect(screen.getByLabelText('Sonraki sayfa')).not.toBeDisabled();
  });

  it('butona tıklanınca onChange doğru sayfa indeksiyle çağrılır', () => {
    const onChange = vi.fn();
    renderWithLang(<Pagination page={0} totalPages={5} onChange={onChange} />);
    // "2" butonu = 0-tabanlı indeks 1
    screen.getByText('2').click();
    expect(onChange).toHaveBeenCalledWith(1);
    // Sonraki sayfa oku
    screen.getByLabelText('Sonraki sayfa').click();
    expect(onChange).toHaveBeenCalledWith(1);
  });

  it('totalElements verilince özet metnini gösterir', () => {
    renderWithLang(
      <Pagination page={0} totalPages={3} onChange={() => {}} totalElements={42} unitLabel="hisse" />
    );
    // "Toplam 42 hisse · Sayfa 1 / 3" — parçalanmış metin, textContent üzerinden doğrulanır
    const summary = screen.getByText(/Toplam/);
    expect(summary).toBeInTheDocument();
    expect(summary.textContent).toContain('42');
    expect(summary.textContent).toContain('1 / 3');
  });
});
