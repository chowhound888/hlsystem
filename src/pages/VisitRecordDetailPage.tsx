import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import {
  ArrowLeft,
  CalendarDays,
  ChevronRight,
  ClipboardCheck,
  FileDown,
  HeartPulse,
  Phone,
  Printer,
  ShieldCheck,
  Stethoscope,
  Thermometer,
  UserRound,
} from 'lucide-react'
import { fetchVisitRecord } from '../api'
import type { VisitRecord } from '../types'

function formatVisitDate(value: string) {
  return new Intl.DateTimeFormat('zh-CN', { year: 'numeric', month: 'long', day: 'numeric', weekday: 'short', hour: '2-digit', minute: '2-digit', hour12: false }).format(new Date(value))
}

function initials(name: string) {
  return name.slice(-1)
}

function visitTypeClass(type: VisitRecord['visitType']) {
  return type === '急诊' ? 'tag tag-danger' : type === '复诊' ? 'tag tag-purple' : 'tag tag-cyan'
}

export default function VisitRecordDetailPage() {
  const { id } = useParams<{ id: string }>()
  const [record, setRecord] = useState<VisitRecord>()
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    if (!id) {
      setLoading(false)
      return
    }
    let active = true
    setLoading(true)
    fetchVisitRecord(id)
      .then((result) => active && setRecord(result.record))
      .catch(() => active && setRecord(undefined))
      .finally(() => active && setLoading(false))
    return () => { active = false }
  }, [id])

  if (loading) {
    return <div className="not-found"><div className="empty-icon"><ClipboardCheck size={21} /></div><h1>正在加载记录</h1><p>正在从后端获取就诊详情。</p></div>
  }

  if (!record) {
    return <div className="not-found"><div className="empty-icon"><ClipboardCheck size={21} /></div><h1>记录不存在</h1><p>这条就诊记录可能已被移除或暂无访问权限。</p><Link className="button button-primary" to="/visit-records">返回就诊记录</Link></div>
  }

  return (
    <div className="app-shell detail-shell">
      <aside className="sidebar" aria-label="主导航">
        <div className="brand"><div className="brand-mark" aria-hidden="true"><HeartPulse size={19} strokeWidth={2.5} /></div><div><strong>临床工作台</strong><span>HLSYSTEM</span></div></div>
        <div className="sidebar-label">工作空间</div>
        <nav className="nav-list"><Link className="nav-item" to="#"><Stethoscope size={18} /><span>概览</span></Link><Link className="nav-item active" to="/visit-records"><ClipboardCheck size={18} /><span>就诊记录</span><span className="nav-count">24</span></Link><Link className="nav-item" to="#"><UserRound size={18} /><span>患者管理</span></Link></nav>
        <div className="sidebar-bottom"><div className="sidebar-label">系统</div><Link className="nav-item" to="#"><ShieldCheck size={18} /><span>系统设置</span></Link><div className="user-mini"><div className="avatar avatar-green">林</div><div><strong>林医生</strong><span>心内科 · 主治医师</span></div></div></div>
      </aside>

      <main className="main-content">
        <header className="topbar"><div className="breadcrumbs"><Link to="/visit-records">就诊记录</Link><ChevronRight size={14} /><strong>记录详情</strong></div><div className="topbar-actions"><button className="icon-button" aria-label="打印当前记录" onClick={() => window.print()}><Printer size={18} /></button><div className="avatar avatar-navy">林</div></div></header>
        <div className="page-content detail-content">
          <Link className="back-link" to="/visit-records"><ArrowLeft size={16} />返回就诊记录</Link>
          <section className="detail-heading"><div><p className="eyebrow">就诊记录 / {record.id}</p><h1>{record.patient.name} 的就诊详情</h1><p className="page-subtitle">记录创建于 {formatVisitDate(record.visitTime)}</p></div><div className="detail-heading-actions"><span className={visitTypeClass(record.visitType)}>{record.visitType}</span><button className="button button-secondary" onClick={() => window.print()}><Printer size={16} />打印记录</button><button className="button button-secondary" onClick={() => window.alert('导出功能将在接入后端接口后开放。')}><FileDown size={16} />导出 PDF</button></div></section>

          <section className="patient-banner"><div className={`avatar avatar-large avatar-${record.patient.gender === '女' ? 'pink' : 'blue'}`}>{initials(record.patient.name)}</div><div className="patient-banner-info"><h2>{record.patient.name}</h2><p>{record.patient.gender} · {record.patient.age} 岁 <span>患者编号 {record.patient.id}</span></p></div><div className="patient-contact"><span><Phone size={14} /> {record.patient.phone}</span><span><CalendarDays size={14} /> 本次就诊 {record.visitType}</span></div><div className="secure-badge"><ShieldCheck size={15} />授权可见</div></section>

          <section className="visit-meta-grid"><div><span>就诊科室</span><strong>{record.department}</strong></div><div><span>主诊医生</span><strong>{record.doctor} 医生</strong></div><div><span>就诊时间</span><strong>{formatVisitDate(record.visitTime)}</strong></div><div><span>下次随访</span><strong>{record.followUp}</strong></div></section>

          <div className="detail-layout"><div className="detail-main">
            <section className="detail-card"><div className="section-title"><div className="section-icon cyan"><ClipboardCheck size={17} /></div><div><h2>诊疗记录</h2><p>本次就诊的临床记录与判断</p></div></div><div className="clinical-sections"><div><h3>主诉</h3><p>{record.chiefComplaint}</p></div><div><h3>既往史</h3><p>{record.medicalHistory}</p></div><div><h3>诊断</h3><div className="detail-diagnosis">{record.diagnosis.map((item) => <span className="diagnosis-tag" key={item}>{item}</span>)}</div></div><div><h3>处理意见</h3><p>{record.treatmentPlan}</p></div></div></section>
            <section className="detail-card"><div className="section-title"><div className="section-icon green"><Stethoscope size={17} /></div><div><h2>用药与医嘱</h2><p>处方和后续治疗安排</p></div></div>{record.prescriptions.length ? <div className="prescription-list">{record.prescriptions.map((prescription) => <div className="prescription-row" key={prescription.name}><div className="prescription-mark">药</div><div className="prescription-name"><strong>{prescription.name}</strong><span>{prescription.dosage}</span></div><span>{prescription.frequency}</span><span>{prescription.duration}</span></div>)}</div> : <div className="no-prescription">本次就诊暂无药物处方，请按处理意见执行。</div>}<div className="follow-up"><CalendarDays size={16} /><div><strong>复诊建议</strong><span>{record.followUp}</span></div></div></section>
          </div><aside className="detail-side"><section className="detail-card vital-card"><div className="section-title"><div className="section-icon coral"><Thermometer size={17} /></div><div><h2>生命体征</h2><p>就诊时测量数据</p></div></div><div className="vital-grid"><div><span>体温</span><strong>{record.vitalSigns.temperature}</strong></div><div><span>心率</span><strong>{record.vitalSigns.heartRate}</strong></div><div><span>血压</span><strong>{record.vitalSigns.bloodPressure}</strong></div><div><span>血氧</span><strong>{record.vitalSigns.oxygenSaturation}</strong></div></div><div className="vital-note"><HeartPulse size={14} />数据来源：门诊测量</div></section><section className="detail-card access-card"><div className="section-title"><div className="section-icon purple"><ShieldCheck size={17} /></div><div><h2>访问记录</h2><p>数据安全状态</p></div></div><div className="access-row"><span>最后查看</span><strong>林医生 · 刚刚</strong></div><div className="access-row"><span>数据范围</span><strong>本人授权患者</strong></div><div className="access-status"><span className="status-dot" />当前访问已记录</div></section></aside></div>
          <p className="privacy-note"><span className="privacy-lock">⌾</span> 患者信息仅供授权医务人员查看，所有访问都会被记录。</p>
        </div>
      </main>
    </div>
  )
}
