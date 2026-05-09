<template>
  <section class="admin-forbidden surface">
    <span class="admin-forbidden__code">403</span>
    <h1>没有后台访问权限</h1>
    <p>当前账号已登录，但不具备管理员角色。请联系管理员为你授予后台权限。</p>
    <div class="split-actions">
      <RouterLink to="/">
        <el-button>返回社区</el-button>
      </RouterLink>
      <RouterLink to="/admin/login">
        <el-button type="primary">切换后台账号</el-button>
      </RouterLink>
    </div>
  </section>
</template>

<style scoped>
.admin-forbidden {
  display: grid;
  gap: 14px;
  place-items: start;
  width: min(620px, 100%);
  margin: 72px auto 0;
  padding: 30px;
}

.admin-forbidden__code {
  font-size: 14px;
  font-weight: 700;
  letter-spacing: 0.2em;
  color: var(--tc-brand);
}

.admin-forbidden h1 {
  margin: 0;
  font-size: 30px;
}

.admin-forbidden p {
  margin: 0;
  color: var(--tc-text-muted);
  line-height: 1.7;
}
</style>
