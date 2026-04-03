import { defineConfig, devices } from '@playwright/test'
import { fileURLToPath } from 'url'
import path from 'path'

const __dirname = fileURLToPath(new URL('.', import.meta.url))

export default defineConfig({
  testDir: 'e2e',
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  reporter: 'list',
  use: {
    baseURL: 'http://localhost:5173',
    trace: 'on-first-retry',
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
  webServer: [
    {
      // React/Vite dev server — proxies /api to the Spring Boot backend
      command: 'npm run dev',
      url: 'http://localhost:5173',
      reuseExistingServer: !process.env.CI,
      timeout: 30_000,
    },
    {
      // Real Spring Boot backend with WireMock stubs for external services
      command: './gradlew runE2EServer',
      cwd: path.resolve(__dirname, '..'),
      url: 'http://localhost:8080/api/config/keys',
      reuseExistingServer: !process.env.CI,
      timeout: 120_000,
    },
  ],
})
