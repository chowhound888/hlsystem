import { useEffect, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import {
  Activity,
  Bell,
  ChevronLeft,
  ChevronRight,
  ClipboardList,
  FileText,
  LayoutDashboard,
  MoreHorizontal,
  Search,
  Settings,
  Stethoscope,
  Users,
} from 'lucide-react'
import { fetchVisitRecords } from '../api'
import type { VisitRecord } from '../types'

const pageSize = 6

function formatVisitTime(value: string) {
  const date = new Date(value)
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).format(date).replace('/', '月').replace(' ', '日 ')
}

function initials(name: string) {
  return name.slice(-1)
}

function visitTypeClass(type: VisitRecord['visitType']) {
  return type === '急诊' ? 'tag tag-danger' : type === '复诊' ? 'tag tag-purple' : 'tag tag-cyan'
}

export default function VisitRecordsPage() {
  const navigate = useNavigate()
  const [searchParams, setSearchParams] = useSearchParams()
  const [keyword, setKeyword] = useState(searchParams.get('keyword') ?? '')
  const [department, setDepartment] = useState(searchParams.get('department') ?? '全部科室')
  const [dateRange, setDateRange] = useState(searchParams.get('date') ?? '全部时间')
  const currentPage = Number(searchParams.get('page') ?? '1')

  const [visibleRecords, setVisibleRecords] = useState<VisitRecord[]>([])
  const [departments, setDepartments] = useState<string[]>(['全部科室'])
  const [total, setTotal] = useState(0)
  const [totalPages, setTotalPages] = useState(1)
  const [page, setPage] = useState(1)
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState('')
  const [retryKey, setRetryKey] = useState(0)

  useEffect(() => {
    let active = true
    setLoading(true)
    setLoadError('')
    fetchVisitRecords({
      keyword: searchParams.get('keyword') ?? '',
      department: searchParams.get('department') ?? '全部科室',
      date: searchParams.get('date') ?? '全部时间',
      page: currentPage,
      pageSize,
    })
      .then((result) => {
        if (!active) return
        setVisibleRecords(result.items)
        setDepartments(result.departments)
        setTotal(result.total)
        setTotalPages(result.totalPages)
        setPage(result.page)
      })
      .catch(() => active && setLoadError('无法加载就诊记录，请确认后端服务已启动。'))
      .finally(() => active && setLoading(false))
    return () => { active = false }
  }, [currentPage, retryKey, searchParams])

  function updateFilters(next: { keyword?: string; department?: string; date?: string; page?: number }) {
    const params = new URLSearchParams(searchParams)
    if (next.keyword !== undefined) next.keyword ? params.set('keyword', next.keyword) : params.delete('keyword')
    if (next.department !== undefined) next.department !== '全部科室' ? params.set('department', next.department) : params.delete('department')
    if (next.date !== undefined) next.date !== '全部时间' ? params.set('date', next.date) : params.delete('date')
    if (next.page !== undefined && next.page > 1) params.set('page', String(next.page))
    else if (next.page !== undefined) params.delete('page')
    setSearchParams(params)
  }

  function handleSearch(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    updateFilters({ keyword, page: 1 })
  }

  return (
    <div className="app-shell">
      <aside className="sidebar" aria-label="主导航">
        <div className="brand">
          <div className="brand-mark" aria-hidden="true"><Activity size={19} strokeWidth={2.5} /></div>
          <div><strong>临床工作台</strong><span>HLSYSTEM</span></div>
        </div>
        <div className="sidebar-label">工作空间</div>
        <nav className="nav-list">
          <Link className="nav-item" to="#"><LayoutDashboard size={18} /><span>概览</span></Link>
          <Link className="nav-item active" to="/visit-records"><ClipboardList size={18} /><span>就诊记录</span><span className="nav-count">24</span></Link>
          <Link className="nav-item" to="#"><Users size={18} /><span>患者管理</span></Link>
          <Link className="nav-item" to="#"><Stethoscope size={18} /><span>检查中心</span></Link>
        </nav>
        <div className="sidebar-bottom">
          <div className="sidebar-label">系统</div>
          <Link className="nav-item" to="#"><Settings size={18} /><span>系统设置</span></Link>
          <div className="sidebar-help">
            <div className="help-icon"><FileText size={16} /></div>
            <div><strong>需要帮助？</strong><span>查看操作手册</span></div>
            <MoreHorizontal size={17} />
          </div>
          <div className="user-mini"><div className="avatar avatar-green">林</div><div><strong>林医生</strong><span>心内科 · 主治医师</span></div><MoreHorizontal size={16} /></div>
        </div>
      </aside>

      <main className="main-content">
        <header className="topbar">
          <div className="breadcrumbs"><span>工作空间</span><ChevronRight size={14} /><strong>就诊记录</strong></div>
          <div className="topbar-actions"><button className="icon-button" aria-label="查看通知"><Bell size={18} /><span className="notification-dot" /></button><div className="avatar avatar-navy">林</div></div>
        </header>

        <div className="page-content">
          <section className="page-heading">
            <div><p className="eyebrow">临床数据 / 记录管理</p><h1>就诊记录</h1><p className="page-subtitle">查看和管理近期患者的诊疗信息，保持每一次接诊都有迹可循。</p></div>
            <div className="heading-meta"><span className="status-dot" />数据已同步<span className="meta-divider" />最后更新 09:42</div>
          </section>

          <section className="metric-grid" aria-label="就诊概览">
            <div className="metric-card"><div className="metric-icon cyan"><ClipboardList size={19} /></div><div><span>本月就诊</span><strong>128</strong><small className="positive">+12.6% <span>较上月</span></small></div><div className="mini-sparkline"><i /><i /><i /><i /><i /><i /></div></div>
            <div className="metric-card"><div className="metric-icon amber"><Activity size={19} /></div><div><span>待复诊</span><strong>12</strong><small>未来 7 天</small></div><div className="metric-badge amber-bg">需关注</div></div>
            <div className="metric-card"><div className="metric-icon green"><Users size={19} /></div><div><span>服务患者</span><strong>96</strong><small>本月去重统计</small></div><div className="avatar-stack"><span className="avatar avatar-blue">张</span><span className="avatar avatar-pink">陈</span><span className="avatar avatar-gold">李</span><span className="avatar avatar-more">+93</span></div></div>
          </section>

          <section className="records-panel">
            <div className="panel-heading"><div><h2>最近就诊</h2><p>共 {total} 条记录</p></div><button className="button button-secondary"><FileText size={16} />导出记录</button></div>
            <form className="filter-bar" onSubmit={handleSearch}>
              <label className="search-field"><Search size={17} /><input value={keyword} onChange={(event) => setKeyword(event.target.value)} placeholder="搜索患者姓名、就诊编号或诊断" aria-label="搜索患者姓名、就诊编号或诊断" /><kbd>⌘ K</kbd></label>
              <label className="select-field"><span>科室</span><select value={department} onChange={(event) => { setDepartment(event.target.value); updateFilters({ department: event.target.value, page: 1 }) }} aria-label="按科室筛选">{departments.map((item) => <option key={item}>{item}</option>)}</select></label>
              <label className="select-field"><span>时间</span><select value={dateRange} onChange={(event) => { setDateRange(event.target.value); updateFilters({ date: event.target.value, page: 1 }) }} aria-label="按时间筛选"><option>全部时间</option><option>本周</option><option>本月</option></select></label>
              <button className="button button-primary" type="submit">查询</button>
            </form>

            {loading ? <div className="empty-state"><div className="empty-icon"><Activity size={21} /></div><h3>正在加载记录</h3><p>正在从后端获取最新就诊数据。</p></div> : loadError ? <div className="empty-state"><div className="empty-icon"><Activity size={21} /></div><h3>记录加载失败</h3><p>{loadError}</p><button className="button button-secondary" onClick={() => setRetryKey((value) => value + 1)}>重试</button></div> : visibleRecords.length ? <>
              <div className="table-wrap">
                <table className="records-table"><caption className="sr-only">就诊记录列表</caption><thead><tr><th>患者</th><th>科室 / 医生</th><th>诊断</th><th>就诊时间</th><th>类型</th><th><span className="sr-only">操作</span></th></tr></thead><tbody>
                  {visibleRecords.map((record) => <tr key={record.id} tabIndex={0} role="link" onClick={() => navigate(`/visit-records/${record.id}`)} onKeyDown={(event) => { if (event.key === 'Enter' || event.key === ' ') navigate(`/visit-records/${record.id}`) }}>
                    <td><div className="patient-cell"><div className={`avatar avatar-${record.patient.gender === '女' ? 'pink' : 'blue'}`}>{initials(record.patient.name)}</div><div><strong>{record.patient.name}</strong><span>{record.patient.gender} · {record.patient.age} 岁 <em>{record.patient.id}</em></span></div></div></td>
                    <td><div className="department-cell"><strong>{record.department}</strong><span>{record.doctor} 医生</span></div></td>
                    <td><div className="diagnosis-cell">{record.diagnosis.slice(0, 2).map((item) => <span className="diagnosis-tag" key={item}>{item}</span>)}</div></td>
                    <td><span className="time-cell">{formatVisitTime(record.visitTime)}</span></td>
                    <td><span className={visitTypeClass(record.visitType)}>{record.visitType}</span></td>
                    <td><Link className="row-action" to={`/visit-records/${record.id}`} onClick={(event) => event.stopPropagation()}>查看详情<ChevronRight size={15} /></Link></td>
                  </tr>)}
                </tbody></table>
              </div>
              <div className="mobile-records">{visibleRecords.map((record) => <Link className="mobile-record" key={record.id} to={`/visit-records/${record.id}`}><div className="mobile-record-top"><div className="patient-cell"><div className={`avatar avatar-${record.patient.gender === '女' ? 'pink' : 'blue'}`}>{initials(record.patient.name)}</div><div><strong>{record.patient.name}</strong><span>{record.patient.gender} · {record.patient.age} 岁</span></div></div><span className={visitTypeClass(record.visitType)}>{record.visitType}</span></div><div className="mobile-record-meta"><span>{record.department} · {record.doctor} 医生</span><span>{formatVisitTime(record.visitTime)}</span></div><div className="diagnosis-cell">{record.diagnosis.map((item) => <span className="diagnosis-tag" key={item}>{item}</span>)}</div></Link>)}</div>
              <div className="pagination"><span>显示 {(page - 1) * pageSize + 1}-{Math.min(page * pageSize, total)} 条，共 {total} 条</span><div className="pagination-actions"><button aria-label="上一页" disabled={page === 1} onClick={() => updateFilters({ page: page - 1 })}><ChevronLeft size={16} /></button><span className="current-page">{page}</span><span>/ {totalPages}</span><button aria-label="下一页" disabled={page === totalPages} onClick={() => updateFilters({ page: page + 1 })}><ChevronRight size={16} /></button></div></div>
            </> : <div className="empty-state"><div className="empty-icon"><Search size={21} /></div><h3>没有找到匹配的记录</h3><p>请尝试调整关键词或筛选条件。</p><button className="button button-secondary" onClick={() => { setKeyword(''); setDepartment('全部科室'); setDateRange('全部时间'); setSearchParams({}) }}>清除筛选</button></div>}
          </section>
          <p className="privacy-note"><span className="privacy-lock">⌾</span> 患者信息仅供授权医务人员查看，所有访问都会被记录。</p>
        </div>
      </main>
    </div>
  )
}
