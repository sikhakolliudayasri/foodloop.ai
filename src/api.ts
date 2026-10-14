export type Role = "donor" | "recipient" | "coordinator";

export type DonorType = "restaurant_stall" | "supermarket_dmart";

export type User = {
  id: number;
  name: string;
  email: string;
  role: Role;
  org_type?: string;
  phone?: string;
  city?: string;
};

export type Donation = {
  id: number;
  donor_id: number;
  donor_name?: string;
  donor_phone?: string;
  donor_type?: DonorType;
  title: string;
  description?: string;
  category: string;
  dietary?: string;
  original_price?: number | null;
  expiry_date?: string | null;
  image_emoji?: string;
  quantity: number;
  remaining_quantity: number;
  unit: string;
  usable_until: string;
  storage: string;
  allergens: string[];
  pickup_address: string;
  latitude: number;
  longitude: number;
  pickup_start: string;
  pickup_end: string;
  packaging: string;
  safety_verified: boolean;
  status: string;
  created_at: string;
};

export type FoodRequest = {
  id: number;
  recipient_id: number;
  organization: string;
  categories: string[];
  requested_quantity: number;
  unmet_quantity: number;
  unit: string;
  urgency: number;
  storage: string[];
  excluded_allergens: string[];
  latitude: number;
  longitude: number;
  delivery_address: string;
  needed_by: string;
  status: string;
  created_at: string;
};

export type Match = {
  donation_id: number;
  request_id: number;
  organization: string;
  quantity: number;
  unit: string;
  distance_km: number;
  score: number;
  explanation: string;
};

export type Allocation = {
  id: number;
  donation_id: number;
  donation_title: string;
  donor_id?: number;
  donor_name?: string;
  donor_phone?: string;
  donor_type?: DonorType;
  pickup_address?: string;
  request_id?: number | null;
  recipient_id?: number | null;
  organization: string;
  quantity: number;
  unit: string;
  status: string;
  claim_reason?: string;
  beneficiary_type?: string;
  contact_phone?: string;
  pickup_code?: string;
  donor_notes?: string;
  estimated_pickup?: string | null;
  created_at: string;
};

export type DirectClaimPayload = {
  quantity: number;
  claim_reason: string;
  beneficiary_type?: string;
  contact_phone?: string;
  organization_name?: string;
  estimated_pickup?: string;
};

export type DonorDecisionPayload = {
  accept: boolean;
  donor_notes?: string;
};

export type Dashboard = {
  role: Role;
  active_donations: number;
  active_requests: number;
  pending_matches: number;
  scheduled_pickups: number;
  completed_deliveries: number;
  listed_quantity: number;
  delivered_quantity: number;
  unmet_demand: number;
  category_distribution: Record<string, number>;
  data_notice: string;
};

const API_URL = import.meta.env.VITE_API_URL ?? "http://localhost:8000/api";
let accessToken = sessionStorage.getItem("foodloop-token");

export function setAccessToken(token: string | null) {
  accessToken = token;
  if (token) sessionStorage.setItem("foodloop-token", token);
  else sessionStorage.removeItem("foodloop-token");
}

export async function api<T>(path: string, options: RequestInit = {}): Promise<T> {
  const headers = new Headers(options.headers);
  headers.set("Content-Type", "application/json");
  if (accessToken) headers.set("Authorization", `Bearer ${accessToken}`);
  const response = await fetch(`${API_URL}${path}`, { ...options, headers });
  const body = await response.json().catch(() => ({}));
  if (!response.ok) {
    const detail = body.detail;
    throw new Error(typeof detail === "string" ? detail : "The request could not be completed.");
  }
  return body as T;
}

export const send = (method: string, body: unknown): RequestInit => ({ method, body: JSON.stringify(body) });

export const categoryLabels: Record<string, string> = {
  prepared_meals: "Prepared meals",
  bakery: "Bakery & Breads",
  produce: "Fresh Produce & Fruits",
  packaged: "Packaged Groceries & FMCG",
  dairy: "Dairy & Chilled",
};

export const statusLabels: Record<string, string> = {
  available: "Available",
  matched: "Fully Reserved",
  pending_donor: "Awaiting Donor Review",
  pending_recipient: "Awaiting Recipient Review",
  accepted: "Accepted · Pending Final Approval",
  approved: "Claim Approved · Ready for Pickup",
  collected: "Picked Up · En Route",
  delivered: "Delivered & Distributed",
  rejected: "Declined",
};