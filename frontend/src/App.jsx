import { useEffect, useMemo, useState } from 'react'
import WaterMap from './WaterMap.jsx'
import LocalityLedger from './LocalityLedger.jsx'
import { fetchLocalities } from './api.js'
import { STATUS_CONFIG, STATUS_ORDER } from './statusConfig.js'
import './App.css'

export default function App() {
  const [localities, setLocalities] = useState([])
  const [status, setStatus] = useState('all')
  const [search, setSearch] = useState('')
  const [selectedId, setSelectedId] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    setError(null)
    fetchLocalities({ status: status === 'all' ? undefined : status, search: search || undefined })
      .then((data) => {
        if (!cancelled) setLocalities(data)
      })
      .catch((err) => {
        if (!cancelled) setError(err.message)
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [status, search])

  const counts = useMemo(() => {
    const tally = { MUNICIPAL: 0, TANKER_DEPENDENT: 0, MIXED: 0, PIPELINE_IN_PROGRESS: 0 }
    for (const loc of localities) tally[loc.status] = (tally[loc.status] ?? 0) + 1
    return tally
  }, [localities])

  return (
    <div className="app">
      <header className="app-header">
        <div className="app-header-inner">
          <div>
            <p className="app-eyebrow">Pune Municipal Corporation &middot; Ward Register</p>
            <h1 className="app-title">Where the Water Reaches</h1>
            <p className="app-subtitle">
              A citizen-tracked record of piped municipal supply, tanker dependency, and pipeline
              work across PMC localities.
            </p>
          </div>
        </div>
      </header>

      <div className="controls">
        <input
          type="search"
          className="search-input"
          placeholder="Search a locality, e.g. Wagholi"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          aria-label="Search localities"
        />
        <div className="filter-chips" role="group" aria-label="Filter by status">
          <button
            className={`chip ${status === 'all' ? 'is-active' : ''}`}
            onClick={() => setStatus('all')}
          >
            All ({localities.length})
          </button>
          {STATUS_ORDER.map((key) => {
            const config = STATUS_CONFIG[key]
            return (
              <button
                key={key}
                className={`chip ${status === key ? 'is-active' : ''}`}
                style={{ '--chip-color': config.color, '--chip-bg': config.bg }}
                onClick={() => setStatus(key)}
              >
                {config.short} ({counts[key] ?? 0})
              </button>
            )
          })}
        </div>
      </div>

      <main className="layout">
        <section className="map-pane">
          {error ? (
            <div className="empty-state">
              <p>
                <strong>Can't reach the API.</strong> Make sure the Spring Boot backend is running
                on <code>localhost:8080</code>.
              </p>
              <p className="empty-state-detail">{error}</p>
            </div>
          ) : (
            <WaterMap localities={localities} selectedId={selectedId} onSelect={setSelectedId} />
          )}
        </section>

        <section className="ledger-pane">
          <h2 className="ledger-pane-title">Register</h2>
          {loading ? (
            <p className="ledger-empty">Loading...</p>
          ) : (
            <LocalityLedger localities={localities} selectedId={selectedId} onSelect={setSelectedId} />
          )}
        </section>
      </main>

      <footer className="app-footer">
        <p>
          Status entries are compiled from public reporting, not a live PMC feed &mdash; each row
          cites its source and the date it was last checked. Spot something outdated? The API
          accepts citizen reports at <code>POST /api/localities/reports</code> for review.
        </p>
      </footer>
    </div>
  )
}
