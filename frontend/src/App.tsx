import { Routes, Route, NavLink } from 'react-router-dom'
import ConfigView from './views/ConfigView'
import CategoryRulesView from './views/CategoryRulesView'
import PendingOrdersView from './views/PendingOrdersView'
import LogsView from './views/LogsView'
import GetHelpView from './views/GetHelpView'
import './App.css'

function App() {
  return (
    <div className="cf-app-shell">
      <nav className="cf-nav">
        <span className="cf-nav-brand">
          <span>▶</span> Budget Sortbot
        </span>
        <ul className="cf-nav-links">
          <li>
            <NavLink to="/" end data-testid="nav-config">
              Configuration
            </NavLink>
          </li>
          <li>
            <NavLink to="/categories" data-testid="nav-categories">
              Category Rules
            </NavLink>
          </li>
          <li>
            <NavLink to="/orders" data-testid="nav-orders">
              Pending Orders
            </NavLink>
          </li>
          <li>
            <NavLink to="/logs" data-testid="nav-logs">
              Logs
            </NavLink>
          </li>
          <li>
            <NavLink to="/help" data-testid="nav-help">
              Get Help
            </NavLink>
          </li>
        </ul>
      </nav>
      <main className="cf-main">
        <Routes>
          <Route path="/" element={<ConfigView />} />
          <Route path="/categories" element={<CategoryRulesView />} />
          <Route path="/orders" element={<PendingOrdersView />} />
          <Route path="/logs" element={<LogsView />} />
          <Route path="/help" element={<GetHelpView />} />
        </Routes>
      </main>
    </div>
  )
}

export default App
