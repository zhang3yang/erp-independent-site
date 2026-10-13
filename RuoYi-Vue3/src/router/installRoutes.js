const installedRouteTrees = new WeakMap()

export function installRouteTrees(router, routes, isExternalRoute) {
  const installedRoutes = getInstalledRoutes(router)

  routes.forEach(route => {
    if (isExternalRoute(route.path)) return

    const identity = getRouteIdentity(route)
    const signature = getRouteSignature(route)
    const installedRoute = installedRoutes.get(identity)

    if (installedRoute) {
      if (installedRoute.signature !== signature) {
        installedRoute.remove()
        installedRoutes.set(identity, { signature, remove: router.addRoute(route) })
      }
      return
    }

    if (isRouteRegistered(router, route)) return
    installedRoutes.set(identity, { signature, remove: router.addRoute(route) })
  })
}

function getInstalledRoutes(router) {
  if (!installedRouteTrees.has(router)) installedRouteTrees.set(router, new Map())
  return installedRouteTrees.get(router)
}

function getRouteIdentity(route) {
  return route.name ? `name:${String(route.name)}` : `path:${resolveRoutePath('', route.path)}`
}

function getRouteSignature(route, parentPath = '') {
  const path = resolveRoutePath(parentPath, route.path)
  return JSON.stringify({
    name: route.name || '',
    path,
    redirect: route.redirect || '',
    meta: route.meta || {},
    component: route.__componentKey || '',
    children: (route.children || []).map(child => JSON.parse(getRouteSignature(child, path)))
  })
}

function resolveRoutePath(parentPath, routePath) {
  if (!routePath) return parentPath || '/'
  if (routePath.startsWith('/')) return routePath.replace(/\/+/g, '/')
  return `${parentPath}/${routePath}`.replace(/\/+/g, '/')
}

function isRouteRegistered(router, route) {
  if (route.name) return router.hasRoute(route.name)
  const routePath = resolveRoutePath('', route.path)
  return router.getRoutes().some(record => record.path === routePath)
}
