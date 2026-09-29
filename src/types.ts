export type Gender = '男' | '女'
export type VisitType = '门诊' | '复诊' | '急诊'

export type Prescription = {
  name: string
  dosage: string
  frequency: string
  duration: string
}

export type VisitRecord = {
  id: string
  patient: {
    id: string
    name: string
    gender: Gender
    age: number
    phone: string
  }
  department: string
  doctor: string
  visitTime: string
  visitType: VisitType
  chiefComplaint: string
  diagnosis: string[]
  medicalHistory: string
  vitalSigns: {
    temperature: string
    heartRate: string
    bloodPressure: string
    oxygenSaturation: string
  }
  treatmentPlan: string
  prescriptions: Prescription[]
  followUp: string
}
