import { Routes, Route, NavLink } from 'react-router-dom'
import ConfigView from './views/ConfigView'
import CategoryRulesView from './views/CategoryRulesView'
import PendingOrdersView from './views/PendingOrdersView'
import LogsView from './views/LogsView'
import './App.css'

function App() {
  return (
    <>
      <nav>
        <NavLink to="/">Configuration</NavLink>
        <NavLink to="/categories">Category Rules</NavLink>
        <NavLink to="/orders">Pending Orders</NavLink>
        <NavLink to="/logs">Logs</NavLink>
      </nav>
      <main>
        <Routes>
          <Route path="/" element={<ConfigView />} />
          <Route path="/categories" element={<CategoryRulesView />} />
          <Route path="/orders" element={<PendingOrdersView />} />
          <Route path="/logs" element={<LogsView />} />
        </Routes>
      </main>
    </>
  )
}

export default App
