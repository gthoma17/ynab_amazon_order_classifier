import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import App from '../App'

describe('App navigation', () => {
  it('renders navigation links for all four views', () => {
    render(
      <MemoryRouter initialEntries={['/']}>
        <App />
      </MemoryRouter>
    )
    expect(screen.getByRole('link', { name: /configuration/i })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /category rules/i })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /pending orders/i })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /logs/i })).toBeInTheDocument()
  })

  it('renders Config view at /', () => {
    render(
      <MemoryRouter initialEntries={['/']}>
        <App />
      </MemoryRouter>
    )
    expect(screen.getByRole('heading', { name: /api keys/i })).toBeInTheDocument()
  })

  it('renders Category Rules view at /categories', () => {
    render(
      <MemoryRouter initialEntries={['/categories']}>
        <App />
      </MemoryRouter>
    )
    expect(screen.getByRole('heading', { name: /category rules/i })).toBeInTheDocument()
  })

  it('renders Pending Orders view at /orders', () => {
    render(
      <MemoryRouter initialEntries={['/orders']}>
        <App />
      </MemoryRouter>
    )
    expect(screen.getByRole('heading', { name: /pending orders/i })).toBeInTheDocument()
  })

  it('renders Logs view at /logs', () => {
    render(
      <MemoryRouter initialEntries={['/logs']}>
        <App />
      </MemoryRouter>
    )
    expect(screen.getByRole('heading', { name: /sync logs/i })).toBeInTheDocument()
  })
})
