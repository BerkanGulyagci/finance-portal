import axios from 'axios';

const BASE_URL = 'http://localhost:8080';

export async function getIpos() {
  const { data: wrapper } = await axios.get(`${BASE_URL}/api/market/ipo`);
  return wrapper.data ?? [];
}
