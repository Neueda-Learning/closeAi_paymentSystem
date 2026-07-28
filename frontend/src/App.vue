<template>
  <div class="app">
    <header class="header">
      <router-link to="/payments" class="logo">Payment System</router-link>
      <nav class="nav">
        <router-link to="/payments" class="nav-link" active-class="active">Payments</router-link>
        <router-link to="/payments/create" class="nav-link" active-class="active">Create</router-link>
        <router-link to="/reports" class="nav-link" active-class="active">Reports</router-link>
      </nav>
    </header>
    <main class="main">
      <router-view />
    </main>
  </div>
</template>

<style>
/* ===== Revolut-inspired global reset ===== */
@import url('https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700;800&display=swap');

*, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }
body { font-family: Inter, system-ui, -apple-system, sans-serif; background: #fff; color: #191c1f; -webkit-font-smoothing: antialiased; }

.app { min-height: 100vh; }
.header { display: flex; align-items: center; padding: 0 40px; height: 64px; border-bottom: 1px solid #f4f4f4; }
.logo { font-size: 20px; font-weight: 700; color: #191c1f; text-decoration: none; letter-spacing: -0.2px; margin-right: 36px; }
.nav { display: flex; gap: 24px; }
.nav-link { font-size: 15px; font-weight: 600; color: #999; text-decoration: none; padding-bottom: 2px; letter-spacing: 0.16px; transition: color 0.15s; }
.nav-link:hover, .nav-link.active { color: #191c1f; }
.main {
  width: 100%;
  max-width: none;
  margin: 0 auto;
  padding: clamp(20px, 3vw, 40px) clamp(16px, 2vw, 24px);
}
</style>
