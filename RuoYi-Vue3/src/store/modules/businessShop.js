import cache from '@/plugins/cache'

const CACHE_KEY = 'platformBusinessShop'

const useBusinessShopStore = defineStore('businessShop', {
  state: () => ({ selectedShop: cache.session.getJSON(CACHE_KEY) || null }),
  getters: {
    shopCode: state => state.selectedShop?.shopCode || '',
    shopName: state => state.selectedShop?.shopName || ''
  },
  actions: {
    selectShop(shop) {
      this.selectedShop = shop ? { shopCode: shop.shopCode, shopName: shop.shopName } : null
      if (this.selectedShop) cache.session.setJSON(CACHE_KEY, this.selectedShop)
      else cache.session.remove(CACHE_KEY)
    },
    clear() {
      this.selectShop(null)
    }
  }
})

export default useBusinessShopStore
