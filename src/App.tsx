import { useEffect, useMemo, useState, type FormEvent } from "react";
import {
  Activity, ArrowDownToLine, ArrowRight, Boxes, Check, ChevronDown, CircleAlert,
  ClipboardList, Clock3, Compass, Eye, EyeOff, HandHeart, Leaf, LogOut, MapPin,
  Menu, PackageCheck, Phone, Plus, QrCode, RefreshCw, Search, Settings2, ShieldCheck,
  Sparkles, Store, Tag, Truck, Utensils, X,
} from "lucide-react";
import {
  Area, AreaChart, CartesianGrid, Cell, Pie, PieChart, ResponsiveContainer,
  Tooltip, XAxis, YAxis,
} from "recharts";
import {
  api, categoryLabels, send, setAccessToken, statusLabels,
  type Allocation, type Dashboard, type Donation, type DonorDecisionPayload,
  type FoodRequest, type Match, type Role, type User,
} from "./api";

type Page = "explore" | "overview" | "donations" | "requests" | "claims" | "matching" | "routes" | "impact" | "settings";
type AuthResult = { access_token: string; user: User };
type MatchResult = { matches: Match[]; rejected: { donation_id: number; reasons: { request_id: number; reason: string }[] }[] };
type LoginMode = "login" | "register";

const categoryOptions = Object.entries(categoryLabels);
const storageOptions = ["ambient", "refrigerated", "frozen", "hot_holding"];
const colors = ["#2d6a4f", "#e07a5f", "#3d5a80", "#ee9b00", "#9b5de5"];

function prettyRole(role: Role) {
  return role === "coordinator" ? "Logistics Coordinator" : role === "recipient" ? "Recipient Organization (NGO)" : "Food Donor";
}

function readable(value: string) {
  return value.replaceAll("_", " ").replace(/\b\w/g, (letter) => letter.toUpperCase());
}

function dateField(hoursFromNow: number) {
  const date = new Date(Date.now() + hoursFromNow * 3600000);
  date.setMinutes(date.getMinutes() - date.getTimezoneOffset());
  return date.toISOString().slice(0, 16);
}

function toIso(value: string) {
  return new Date(value).toISOString();
}

function StatusPill({ value }: { value: string }) {
  const tone = value === "delivered" ? "good" : value === "rejected" ? "muted" : value === "available" || value === "open" ? "fresh" : value === "pending_donor" ? "attention" : "warm";
  return <span className={`status-pill ${tone}`}><i />{statusLabels[value] ?? readable(value)}</span>;
}

function AuthScreen({ onAuthenticated }: { onAuthenticated: (result: AuthResult) => void }) {
  const [mode, setMode] = useState<LoginMode>("login");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [role, setRole] = useState<Role>("donor");
  const [donorType, setDonorType] = useState<"restaurant_stall" | "supermarket_dmart">("restaurant_stall");
  const [showPassword, setShowPassword] = useState(false);

  async function handleDemoLogin(preset: "restaurant" | "supermarket" | "recipient" | "coordinator") {
    setError("");
    setBusy(true);
    try {
      const result = await api<AuthResult>(`/auth/demo-login/${preset}`, { method: "POST" });
      onAuthenticated(result);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Unable to sign in with demo preset.");
    } finally {
      setBusy(false);
    }
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError("");
    setBusy(true);
    const data = new FormData(event.currentTarget);
    const payload: Record<string, string> = {
      email: String(data.get("email")).trim(),
      password: String(data.get("password")),
    };
    if (mode === "register") {
      payload.name = String(data.get("name")).trim();
      payload.role = role;
      payload.org_type = role === "donor" ? donorType : role === "recipient" ? "ngo" : "coordinator";
      payload.phone = String(data.get("phone") ?? "").trim();
      payload.city = String(data.get("city") ?? "").trim();
      if (role === "coordinator") payload.coordinator_invite = String(data.get("invite") ?? "");
    }
    try {
      const result = await api<AuthResult>(`/auth/${mode === "login" ? "login" : "register"}`, send("POST", payload));
      onAuthenticated(result);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Unable to sign in.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <main className="auth-shell">
      <section className="auth-art">
        <div className="auth-brand"><span className="brand-mark"><Leaf size={20} /></span> FoodLoop <b>AI</b></div>
        <div className="auth-message">
          <span className="eyebrow light"><span /> COMMUNITY FOOD RESCUE NETWORK</span>
          <h1>Zero food wasted.<br />100% hunger addressed.</h1>
          <p>
            Connect fresh surplus meals from restaurants & food stalls, plus near-expiry items from supermarkets & D-Marts, with verified NGOs and shelters in real-time.
          </p>
          <div className="auth-features-list">
            <div className="feature-item">
              <span className="feat-icon"><Utensils size={18} /></span>
              <div>
                <b>Restaurants & Food Stalls</b>
                <span>Post unused cooked meals and buffet surplus safely with countdown timers.</span>
              </div>
            </div>
            <div className="feature-item">
              <span className="feat-icon"><Store size={18} /></span>
              <div>
                <b>Supermarkets & D-Marts</b>
                <span>List near-expiry grocery items, dairy, fruits, and bakery goods before best-before dates.</span>
              </div>
            </div>
            <div className="feature-item">
              <span className="feat-icon"><HandHeart size={18} /></span>
              <div>
                <b>Direct Claims with Purpose</b>
                <span>Shelters and NGOs submit proper reasons directly to donors for instant approval and pickup passes.</span>
              </div>
            </div>
          </div>
        </div>
        <div className="art-footer"><span>FOODLOOP OPERATIONS</span><span>v2.0 · RESCUE HUB</span></div>
      </section>

      <section className="auth-panel">
        <div className="auth-panel-top">
          <span>LIVE COMMUNITY NETWORK</span>
          <span className="online-dot" /> Bengaluru & Metro Zones
        </div>

        <div className="demo-presets-banner">
          <p className="demo-title"><Sparkles size={14} /> Quick One-Click Demo Access:</p>
          <div className="demo-buttons">
            <button type="button" className="demo-pill" onClick={() => void handleDemoLogin("restaurant")} disabled={busy}>
              🍲 <b>Paradise Biryani</b> <small>(Restaurant)</small>
            </button>
            <button type="button" className="demo-pill" onClick={() => void handleDemoLogin("supermarket")} disabled={busy}>
              🏬 <b>D-Mart Supercenter</b> <small>(Supermarket)</small>
            </button>
            <button type="button" className="demo-pill" onClick={() => void handleDemoLogin("recipient")} disabled={busy}>
              🤝 <b>Hope Haven Shelter</b> <small>(NGO)</small>
            </button>
            <button type="button" className="demo-pill" onClick={() => void handleDemoLogin("coordinator")} disabled={busy}>
              🛡️ <b>City Coordinator</b>
            </button>
          </div>
        </div>

        <div className="auth-form-wrap">
          <div className="auth-tabs">
            <button className={`auth-tab ${mode === "login" ? "active" : ""}`} onClick={() => { setMode("login"); setError(""); }}>
              Sign In
            </button>
            <button className={`auth-tab ${mode === "register" ? "active" : ""}`} onClick={() => { setMode("register"); setError(""); }}>
              Create Account
            </button>
          </div>

          <div className="auth-heading">
            <h2>{mode === "login" ? "Welcome back to FoodLoop." : "Join the Food Rescue Movement."}</h2>
            <p>{mode === "login" ? "Sign in to manage your food donations, claims, or handoffs." : "Register your restaurant, supermarket, or NGO to start connecting surplus food."}</p>
          </div>

          <form onSubmit={submit} className="form-stack">
            {mode === "register" && (
              <>
                <label>
                  Organization or Full Name
                  <input required name="name" minLength={2} placeholder="e.g. Spice Garden Restaurant / Sunshine NGO" />
                </label>

                <div className="role-selector-wrap">
                  <span className="input-heading">I am joining as:</span>
                  <div className="role-cards-grid">
                    <button
                      type="button"
                      className={`role-select-card ${role === "donor" ? "selected" : ""}`}
                      onClick={() => setRole("donor")}
                    >
                      <Store size={20} />
                      <b>Food Donor</b>
                      <small>Restaurants, stalls, supermarkets, D-Marts, caterers</small>
                    </button>
                    <button
                      type="button"
                      className={`role-select-card ${role === "recipient" ? "selected" : ""}`}
                      onClick={() => setRole("recipient")}
                    >
                      <HandHeart size={20} />
                      <b>Recipient NGO</b>
                      <small>Shelters, orphanages, community kitchens, food relief</small>
                    </button>
                    <button
                      type="button"
                      className={`role-select-card ${role === "coordinator" ? "selected" : ""}`}
                      onClick={() => setRole("coordinator")}
                    >
                      <ShieldCheck size={20} />
                      <b>Coordinator</b>
                      <small>Logistics coordinator or volunteer manager</small>
                    </button>
                  </div>
                </div>

                {role === "donor" && (
                  <div className="donor-subtype-select">
                    <span className="input-heading">Donor Category:</span>
                    <div className="subtype-toggle">
                      <button
                        type="button"
                        className={donorType === "restaurant_stall" ? "active" : ""}
                        onClick={() => setDonorType("restaurant_stall")}
                      >
                        🍲 Restaurant / Food Stall / Dhaba <small>(Cooked surplus food)</small>
                      </button>
                      <button
                        type="button"
                        className={donorType === "supermarket_dmart" ? "active" : ""}
                        onClick={() => setDonorType("supermarket_dmart")}
                      >
                        🏬 Supermarket / D-Mart / Grocery <small>(Near-expiry packaged goods)</small>
                      </button>
                    </div>
                  </div>
                )}

                {role === "coordinator" && (
                  <label>
                    Coordinator Invitation Code
                    <input required name="invite" placeholder="Provided by your administrator" />
                  </label>
                )}

                <div className="split-field">
                  <label>
                    Contact Mobile Number
                    <input required name="phone" placeholder="+91 98765 43210" />
                  </label>
                  <label>
                    City / Zone
                    <input required name="city" defaultValue="Bengaluru" placeholder="City name" />
                  </label>
                </div>
              </>
            )}

            <label>
              Email Address
              <input required name="email" type="email" autoComplete="email" placeholder="contact@organization.org" />
            </label>

            <label className="password-field-label">
              <span>Password</span>
              <div className="password-input-wrap">
                <input
                  required
                  name="password"
                  type={showPassword ? "text" : "password"}
                  minLength={mode === "register" ? 8 : 1}
                  autoComplete={mode === "login" ? "current-password" : "new-password"}
                  placeholder={mode === "register" ? "At least 8 characters" : "Your password"}
                />
                <button type="button" className="pw-toggle" onClick={() => setShowPassword(!showPassword)} tabIndex={-1}>
                  {showPassword ? <EyeOff size={16} /> : <Eye size={16} />}
                </button>
              </div>
            </label>

            {error && <div className="form-error"><CircleAlert size={16} />{error}</div>}

            <button className="button primary full" disabled={busy}>
              {busy ? "Connecting to FoodLoop…" : mode === "login" ? "Sign In to Workspace" : "Create Account & Start"}
              <ArrowRight size={16} />
            </button>
          </form>

          <p className="auth-privacy">
            <ShieldCheck size={14} /> Verified community standards: All food listings declare safety, allergens, and usable timelines.
          </p>
        </div>

        <footer className="auth-bottom">
          <span>FoodLoop AI · Zero Waste Operations</span>
          <span>Terms & Safety Guidelines · Support</span>
        </footer>
      </section>
    </main>
  );
}

function App() {
  const [user, setUser] = useState<User | null>(() => {
    const cached = sessionStorage.getItem("foodloop-user");
    return cached ? JSON.parse(cached) as User : null;
  });
  const [page, setPage] = useState<Page>("explore");
  const [dashboard, setDashboard] = useState<Dashboard | null>(null);
  const [donations, setDonations] = useState<Donation[]>([]);
  const [exploreListings, setExploreListings] = useState<Donation[]>([]);
  const [requests, setRequests] = useState<FoodRequest[]>([]);
  const [allocations, setAllocations] = useState<Allocation[]>([]);
  const [matches, setMatches] = useState<Match[]>([]);
  const [rejections, setRejections] = useState<MatchResult["rejected"]>([]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [mobileNav, setMobileNav] = useState(false);

  // Modals
  const [claimTarget, setClaimTarget] = useState<Donation | null>(null);
  const [pickupPassTarget, setPickupPassTarget] = useState<Allocation | null>(null);
  const [modal, setModal] = useState<"donation" | "request" | null>(null);

  function acceptAuth(result: AuthResult) {
    setAccessToken(result.access_token);
    sessionStorage.setItem("foodloop-user", JSON.stringify(result.user));
    setUser(result.user);
    setPage("explore");
  }

  function signOut() {
    setAccessToken(null);
    sessionStorage.removeItem("foodloop-user");
    setUser(null);
    setPage("explore");
    setDashboard(null);
  }

  async function loadExploreData() {
    try {
      const items = await api<Donation[]>("/donations/explore");
      setExploreListings(items);
    } catch {
      // Fallback or retry
    }
  }

  async function refresh() {
    if (!user) {
      void loadExploreData();
      return;
    }
    setBusy(true);
    setError("");
    try {
      const [nextDashboard, nextDonations, nextRequests, nextAllocations, nextExplore] = await Promise.all([
        api<Dashboard>("/dashboard").catch(() => null),
        api<Donation[]>("/donations").catch(() => []),
        api<FoodRequest[]>("/requests").catch(() => []),
        api<Allocation[]>("/allocations").catch(() => []),
        api<Donation[]>("/donations/explore").catch(() => []),
      ]);
      setDashboard(nextDashboard);
      setDonations(nextDonations);
      setRequests(nextRequests);
      setAllocations(nextAllocations);
      setExploreListings(nextExplore);

      if (user.role === "coordinator") {
        const result = await api<MatchResult>("/matches").catch(() => ({ matches: [], rejected: [] }));
        setMatches(result.matches);
        setRejections(result.rejected);
      }
    } catch (cause) {
      const message = cause instanceof Error ? cause.message : "Could not reach the FoodLoop API.";
      setError(message.includes("fetch") ? "Cannot reach the API. Start the backend at http://localhost:8000 and refresh." : message);
      if (message.includes("Authentication") || message.includes("token")) signOut();
    } finally {
      setBusy(false);
    }
  }

  useEffect(() => {
    void refresh();
  }, [user?.id]);

  useEffect(() => {
    if (!notice) return;
    const timer = window.setTimeout(() => setNotice(""), 4200);
    return () => window.clearTimeout(timer);
  }, [notice]);

  const navigation = useMemo(() => {
    const items: { id: Page; label: string; icon: typeof Activity; badge?: number }[] = [
      { id: "explore", label: "Live Food Hub", icon: Sparkles },
      { id: "overview", label: "Overview", icon: Activity },
      { id: "donations", label: user?.role === "donor" ? "My Listings" : "All Food Offers", icon: Boxes },
      { id: "claims", label: user?.role === "donor" ? "Incoming Claims" : "My Food Claims", icon: HandHeart },
      { id: "requests", label: user?.role === "recipient" ? "My Requests" : "Community Needs", icon: ClipboardList },
    ];
    if (user?.role === "coordinator") items.push({ id: "matching", label: "Match Desk", icon: Sparkles, badge: matches.length });
    items.push({ id: "routes", label: "Pickup & Delivery", icon: Truck }, { id: "impact", label: "Impact", icon: Compass });
    return items;
  }, [user?.role, matches.length]);

  async function submitDonation(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    const donorTypeVal = String(data.get("donor_type") || "restaurant_stall");
    const payload = {
      title: String(data.get("title")),
      category: String(data.get("category")),
      donor_type: donorTypeVal,
      description: String(data.get("description") ?? ""),
      dietary: String(data.get("dietary") ?? "veg"),
      original_price: data.get("original_price") ? Number(data.get("original_price")) : null,
      expiry_date: data.get("expiry_date") ? toIso(String(data.get("expiry_date"))) : null,
      image_emoji: donorTypeVal === "restaurant_stall" ? "🍲" : "🏬",
      quantity: Number(data.get("quantity")),
      unit: String(data.get("unit")),
      usable_until: toIso(String(data.get("usable_until"))),
      storage: String(data.get("storage")),
      allergens: String(data.get("allergens")).split(",").map((item) => item.trim()).filter(Boolean),
      pickup_address: String(data.get("pickup_address")),
      latitude: Number(data.get("latitude")),
      longitude: Number(data.get("longitude")),
      pickup_start: toIso(String(data.get("pickup_start"))),
      pickup_end: toIso(String(data.get("pickup_end"))),
      packaging: String(data.get("packaging")),
      safety_verified: data.get("safety_verified") === "on",
    };
    try {
      await api<Donation>("/donations", send("POST", payload));
      setModal(null);
      setPage("donations");
      setNotice("Food item posted! It is now visible on the Live Food Hub.");
      await refresh();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Could not create donation.");
    }
  }

  async function submitDirectClaim(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!claimTarget) return;
    const data = new FormData(event.currentTarget);
    const payload = {
      quantity: Number(data.get("quantity")),
      claim_reason: String(data.get("claim_reason")).trim(),
      beneficiary_type: String(data.get("beneficiary_type")),
      contact_phone: String(data.get("contact_phone")),
      organization_name: String(data.get("organization_name") || user?.name || ""),
      estimated_pickup: data.get("estimated_pickup") ? toIso(String(data.get("estimated_pickup"))) : undefined,
    };
    try {
      const res = await api<Allocation>(`/donations/${claimTarget.id}/claim`, send("POST", payload));
      setClaimTarget(null);
      setPage("claims");
      setNotice("Claim submitted! The donor will review your reason. You can track its status in Claims.");
      setPickupPassTarget(res);
      await refresh();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Could not submit food claim.");
    }
  }

  async function submitDonorDecision(allocationId: number, accept: boolean, notes = "") {
    try {
      const res = await api<Allocation>(`/allocations/${allocationId}/donor-decision`, send("PATCH", { accept, donor_notes: notes } as DonorDecisionPayload));
      setNotice(accept ? "Claim approved! Food is reserved and a Digital Pickup Pass has been generated." : "Claim declined.");
      if (accept) setPickupPassTarget(res);
      await refresh();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Could not save donor decision.");
    }
  }

  async function submitRequest(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    const payload = {
      organization: String(data.get("organization")),
      categories: data.getAll("categories").map(String),
      requested_quantity: Number(data.get("requested_quantity")),
      unit: String(data.get("unit")),
      urgency: Number(data.get("urgency")),
      storage: data.getAll("storage").map(String),
      excluded_allergens: String(data.get("excluded_allergens")).split(",").map((item) => item.trim()).filter(Boolean),
      latitude: Number(data.get("latitude")),
      longitude: Number(data.get("longitude")),
      delivery_address: String(data.get("delivery_address")),
      needed_by: toIso(String(data.get("needed_by"))),
    };
    try {
      await api<FoodRequest>("/requests", send("POST", payload));
      setModal(null);
      setPage("requests");
      setNotice("Community food request saved.");
      await refresh();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Could not create request.");
    }
  }

  async function act(path: string, body?: unknown, success = "Update saved.") {
    try {
      await api(path, body === undefined ? { method: "PATCH" } : send("PATCH", body));
      setNotice(success);
      await refresh();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Action could not be completed.");
    }
  }

  async function propose(match: Match) {
    try {
      await api("/allocations", send("POST", { donation_id: match.donation_id, request_id: match.request_id, quantity: match.quantity }));
      setNotice("Offer sent to the recipient for review.");
      await refresh();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Offer could not be sent.");
    }
  }

  function exportCsv() {
    const rows = [
      ["Metric", "Value"],
      ["Listed quantity", String(dashboard?.listed_quantity ?? 0)],
      ["Delivered quantity", String(dashboard?.delivered_quantity ?? 0)],
      ["Completed deliveries", String(dashboard?.completed_deliveries ?? 0)],
      ["Unmet demand", String(dashboard?.unmet_demand ?? 0)],
    ];
    const csv = rows.map((row) => row.map((cell) => `"${cell.replaceAll('"', '""')}"`).join(",")).join("\n");
    const url = URL.createObjectURL(new Blob([csv], { type: "text/csv" }));
    const link = document.createElement("a");
    link.href = url;
    link.download = "foodloop-impact.csv";
    link.click();
    URL.revokeObjectURL(url);
  }

  if (!user) return <AuthScreen onAuthenticated={acceptAuth} />;

  const pendingClaimsForDonor = allocations.filter((item) => item.status === "pending_donor");
  const totalChart = Object.entries(dashboard?.category_distribution ?? {}).map(([name, value]) => ({
    name: categoryLabels[name] ?? readable(name),
    value,
  }));
  const activePageLabel = navigation.find((item) => item.id === page)?.label ?? "Workspace";

  return (
    <div className="app-shell">
      <aside className={`sidebar ${mobileNav ? "open" : ""}`}>
        <div className="brand-lockup">
          <span className="brand-mark"><Leaf size={19} /></span>
          <span>FoodLoop <b>AI</b><small>FOOD RESCUE HUB</small></span>
        </div>

        <div className="workspace-switch">
          <span className="workspace-avatar">{user.name.slice(0, 1).toUpperCase()}</span>
          <span>
            <b>{user.name}</b>
            <small>{prettyRole(user.role)} {user.city ? `· ${user.city}` : ""}</small>
          </span>
          <ChevronDown size={15} />
        </div>

        <div className="nav-caption">DISCOVER & OPERATE</div>
        <nav className="side-nav" aria-label="Main navigation">
          {navigation.map(({ id, label, icon: Icon, badge }) => (
            <button
              key={id}
              className={page === id ? "nav-item selected" : "nav-item"}
              onClick={() => { setPage(id); setMobileNav(false); }}
            >
              <Icon size={17} />
              <span>{label}</span>
              {badge !== undefined && badge > 0 && <em>{badge}</em>}
              {id === "claims" && user.role === "donor" && pendingClaimsForDonor.length > 0 && (
                <em className="urgent-badge">{pendingClaimsForDonor.length}</em>
              )}
            </button>
          ))}
        </nav>

        <div className="sidebar-bottom">
          <div className="network-card">
            <span className="network-icon"><HandHeart size={17} /></span>
            <div>
              <b>Direct Claiming Active</b>
              <small>Submit reasons · Donors review & approve</small>
            </div>
            <span className="online-dot" />
          </div>
          <button className={`nav-item ${page === "settings" ? "selected" : ""}`} onClick={() => setPage("settings")}>
            <Settings2 size={17} />Settings & Profile
          </button>
          <button className="nav-item signout" onClick={signOut}>
            <LogOut size={17} />Sign out
          </button>
        </div>
      </aside>

      <main className="main-area">
        <header className="topbar">
          <button className="icon-button mobile-menu" aria-label="Open navigation" onClick={() => setMobileNav(!mobileNav)}>
            <Menu size={19} />
          </button>
          <div className="breadcrumbs">
            <span>FoodLoop</span>
            <span className="crumb-divider">/</span>
            <b>{activePageLabel}</b>
          </div>

          <div className="top-actions">
            <button className="button-quick-action" onClick={() => setPage("explore")}>
              <Sparkles size={14} /> Explore Hub
            </button>
            {user.role === "donor" && (
              <button className="button-quick-action primary" onClick={() => setModal("donation")}>
                <Plus size={14} /> Post Food
              </button>
            )}
            <button className="icon-button" title="Refresh records" onClick={() => void refresh()}>
              <RefreshCw size={17} className={busy ? "spin" : ""} />
            </button>
            <span className="top-avatar" title={user.name}>{user.name.slice(0, 1).toUpperCase()}</span>
          </div>
        </header>

        <div className="page-content">
          {error && (
            <div className="alert-banner error">
              <CircleAlert size={17} />
              <span>{error}</span>
              <button onClick={() => setError("")} aria-label="Dismiss"><X size={16} /></button>
            </div>
          )}

          {page === "explore" && (
            <ExplorePage
              user={user}
              listings={exploreListings}
              onClaim={(item) => setClaimTarget(item)}
              onAddNew={() => setModal("donation")}
            />
          )}

          {page === "overview" && (
            <Overview
              user={user}
              dashboard={dashboard}
              donations={donations}
              matches={matches}
              allocations={allocations}
              onNavigate={setPage}
              onOpenDonation={() => setModal("donation")}
              onViewPass={(item) => setPickupPassTarget(item)}
              onDonorDecision={submitDonorDecision}
            />
          )}

          {page === "donations" && (
            <DonationsPage
              user={user}
              donations={donations}
              onAdd={() => setModal("donation")}
              onClaim={(item) => setClaimTarget(item)}
            />
          )}

          {page === "claims" && (
            <ClaimsPage
              user={user}
              allocations={allocations}
              onViewPass={(item) => setPickupPassTarget(item)}
              onDonorDecision={submitDonorDecision}
              onAction={act}
            />
          )}

          {page === "requests" && (
            <RequestsPage
              role={user.role}
              requests={requests}
              onAdd={() => setModal("request")}
            />
          )}

          {page === "matching" && (
            <MatchingPage
              matches={matches}
              rejections={rejections}
              donations={donations}
              onPropose={propose}
            />
          )}

          {page === "routes" && (
            <HandoffsPage
              allocations={allocations}
              donations={donations}
              requests={requests}
              onViewPass={(item) => setPickupPassTarget(item)}
            />
          )}

          {page === "impact" && (
            <ImpactPage dashboard={dashboard} chartData={totalChart} onExport={exportCsv} />
          )}

          {page === "settings" && (
            <SettingsPage user={user} onSignOut={signOut} />
          )}
        </div>
      </main>

      {mobileNav && <button className="nav-scrim" aria-label="Close navigation" onClick={() => setMobileNav(false)} />}

      {claimTarget && (
        <ClaimModal
          user={user}
          donation={claimTarget}
          onClose={() => setClaimTarget(null)}
          onSubmit={submitDirectClaim}
        />
      )}

      {pickupPassTarget && (
        <PickupPassModal
          user={user}
          allocation={pickupPassTarget}
          onClose={() => setPickupPassTarget(null)}
          onAction={act}
        />
      )}

      {modal && (
        <FormModal
          kind={modal}
          user={user}
          onClose={() => setModal(null)}
          onDonation={submitDonation}
          onRequest={submitRequest}
        />
      )}

      {notice && <div role="status" className="toast"><Check size={17} />{notice}</div>}
    </div>
  );
}

function PageHeading({ eyebrow, title, detail, action }: { eyebrow: string; title: string; detail: string; action?: React.ReactNode }) {
  return (
    <div className="page-heading">
      <div>
        <p className="eyebrow">{eyebrow}</p>
        <h1>{title}</h1>
        <p className="heading-detail">{detail}</p>
      </div>
      {action && <div className="heading-action">{action}</div>}
    </div>
  );
}

// ----------------- EXPLORE PAGE (CORE REQUIREMENT) -----------------
function ExplorePage({
  user,
  listings,
  onClaim,
  onAddNew,
}: {
  user: User;
  listings: Donation[];
  onClaim: (item: Donation) => void;
  onAddNew: () => void;
}) {
  const [filterType, setFilterType] = useState<"all" | "restaurant_stall" | "supermarket_dmart">("all");
  const [categoryFilter, setCategoryFilter] = useState("all");
  const [search, setSearch] = useState("");

  const filtered = listings.filter((item) => {
    const matchesType = filterType === "all" || item.donor_type === filterType;
    const matchesCat = categoryFilter === "all" || item.category === categoryFilter;
    const term = search.toLowerCase();
    const matchesSearch = !search || `${item.title} ${item.pickup_address} ${item.donor_name ?? ""} ${item.description ?? ""}`.toLowerCase().includes(term);
    return matchesType && matchesCat && matchesSearch;
  });

  const restaurantCount = listings.filter((d) => d.donor_type === "restaurant_stall").length;
  const supermarketCount = listings.filter((d) => d.donor_type === "supermarket_dmart").length;

  return (
    <div className="explore-container">
      <div className="explore-hero">
        <div className="hero-content">
          <span className="eyebrow light"><Sparkles size={14} /> LIVE CITY RESCUE HUB</span>
          <h1>Fresh Surplus Food & Near-Expiry Deals</h1>
          <p>
            Browse available food from nearby restaurants, stalls, supermarkets, and D-Marts. Community organizations and shelters can claim items directly with a verified reason.
          </p>
          <div className="hero-stats">
            <span className="hero-stat-badge">
              <Utensils size={15} /> <b>{restaurantCount}</b> Restaurant & Stall Offers
            </span>
            <span className="hero-stat-badge">
              <Store size={15} /> <b>{supermarketCount}</b> Supermarket & D-Mart Clearances
            </span>
            <span className="hero-stat-badge live">
              <i /> Live Database Sync
            </span>
          </div>
        </div>
        {user.role === "donor" && (
          <button className="button primary hero-action" onClick={onAddNew}>
            <Plus size={16} /> Post Surplus Food
          </button>
        )}
      </div>

      <div className="explore-toolbar">
        <div className="explore-type-tabs">
          <button
            className={`type-tab ${filterType === "all" ? "active" : ""}`}
            onClick={() => setFilterType("all")}
          >
            All Live Food ({listings.length})
          </button>
          <button
            className={`type-tab ${filterType === "restaurant_stall" ? "active" : ""}`}
            onClick={() => setFilterType("restaurant_stall")}
          >
            🍲 Restaurants & Stalls ({restaurantCount})
          </button>
          <button
            className={`type-tab ${filterType === "supermarket_dmart" ? "active" : ""}`}
            onClick={() => setFilterType("supermarket_dmart")}
          >
            🏬 Supermarkets & D-Marts ({supermarketCount})
          </button>
        </div>

        <div className="explore-search-row">
          <div className="search-box">
            <Search size={16} />
            <input
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="Search dishes, grocery items, D-Mart, or location…"
            />
            {search && <button className="clear-btn" onClick={() => setSearch("")}><X size={14} /></button>}
          </div>

          <div className="filter-select">
            <span>Category:</span>
            <select value={categoryFilter} onChange={(e) => setCategoryFilter(e.target.value)}>
              <option value="all">All Categories</option>
              {categoryOptions.map(([val, label]) => (
                <option key={val} value={val}>{label}</option>
              ))}
            </select>
          </div>
        </div>
      </div>

      <div className="food-cards-grid">
        {filtered.map((item) => {
          const isSupermarket = item.donor_type === "supermarket_dmart";
          const hoursLeft = Math.max(0, Math.ceil((new Date(item.usable_until).getTime() - Date.now()) / 3600000));
          const isUrgent = hoursLeft <= 6;
          const isMine = user.id === item.donor_id;

          return (
            <article className={`food-card ${isSupermarket ? "supermarket-card" : "restaurant-card"}`} key={item.id}>
              <div className="food-card-header">
                <div className="donor-badge-group">
                  <span className={`donor-type-tag ${isSupermarket ? "tag-supermarket" : "tag-restaurant"}`}>
                    {isSupermarket ? <Store size={14} /> : <Utensils size={14} />}
                    {isSupermarket ? "Supermarket / D-Mart" : "Restaurant & Stall Surplus"}
                  </span>
                  {item.dietary && (
                    <span className={`dietary-tag ${item.dietary}`}>
                      {item.dietary === "veg" ? "🥗 Veg" : item.dietary === "vegan" ? "🌱 Vegan" : "🍗 Non-Veg"}
                    </span>
                  )}
                </div>

                <span className={`time-badge ${isUrgent ? "urgent" : ""}`}>
                  <Clock3 size={13} />
                  {hoursLeft < 1 ? "Under 1h left" : `${hoursLeft}h left`}
                </span>
              </div>

              <div className="food-card-body">
                <div className="title-row">
                  <span className="food-emoji">{item.image_emoji || (isSupermarket ? "🏬" : "🍲")}</span>
                  <div>
                    <h3>{item.title}</h3>
                    <p className="donor-store-name">
                      <b>{item.donor_name || "Verified Partner"}</b>
                    </p>
                  </div>
                </div>

                {item.description && <p className="food-description">{item.description}</p>}

                {isSupermarket && (
                  <div className="supermarket-info-box">
                    <div className="price-tag-row">
                      <span className="near-expiry-badge"><Tag size={13} /> Near Expiry Clearance</span>
                      {item.original_price && (
                        <span className="price-compare">
                          <del>₹{item.original_price}</del> <b>FREE RESCUE</b>
                        </span>
                      )}
                    </div>
                    {item.expiry_date && (
                      <div className="expiry-notice">
                        <span>Best Before: <b>{new Date(item.expiry_date).toLocaleDateString([], { month: "short", day: "numeric", hour: "2-digit", minute: "2-digit" })}</b></span>
                      </div>
                    )}
                  </div>
                )}

                <div className="food-stats-strip">
                  <div className="stat-pill">
                    <small>Available</small>
                    <b>{item.remaining_quantity} {item.unit}</b>
                  </div>
                  <div className="stat-pill">
                    <small>Storage</small>
                    <b>{readable(item.storage)}</b>
                  </div>
                  <div className="stat-pill">
                    <small>Packaging</small>
                    <b>{item.packaging ? item.packaging.slice(0, 18) : "Standard"}</b>
                  </div>
                </div>

                <div className="food-location-row">
                  <MapPin size={14} />
                  <span>{item.pickup_address}</span>
                </div>
              </div>

              <div className="food-card-footer">
                {isMine ? (
                  <div className="mine-notice">
                    <ShieldCheck size={14} /> <span>Your Listing</span>
                  </div>
                ) : (
                  <button
                    className="button primary full"
                    onClick={() => onClaim(item)}
                  >
                    <HandHeart size={16} /> Claim This Food (Submit Reason)
                  </button>
                )}
              </div>
            </article>
          );
        })}

        {filtered.length === 0 && (
          <div className="empty-explore-state span-all">
            <Boxes size={32} />
            <h3>No matching food listings found</h3>
            <p>Try clearing your search filters or check back shortly as restaurants and D-Marts post new items daily.</p>
            {user.role === "donor" && (
              <button className="button subtle" onClick={onAddNew}>
                <Plus size={16} /> Post First Listing
              </button>
            )}
          </div>
        )}
      </div>
    </div>
  );
}

// ----------------- DIRECT CLAIM MODAL (WITH REASON) -----------------
function ClaimModal({
  user,
  donation,
  onClose,
  onSubmit,
}: {
  user: User;
  donation: Donation;
  onClose: () => void;
  onSubmit: (e: FormEvent<HTMLFormElement>) => void;
}) {
  const [quantity, setQuantity] = useState(donation.remaining_quantity);
  const [reason, setReason] = useState("");

  const quickReasons = [
    "Evening dinner for 40 resident children at our shelter home.",
    "Daily free community kitchen meal drive outside the general hospital.",
    "Emergency nutritious food packages for 25 migrant families.",
    "Breakfast snack distribution for underprivileged elderly residents.",
  ];

  return (
    <div className="modal-backdrop" onMouseDown={(e) => { if (e.target === e.currentTarget) onClose(); }}>
      <section className="modal claim-modal" role="dialog" aria-modal="true">
        <div className="modal-heading">
          <div>
            <p className="eyebrow">FOOD LOOP DIRECT CLAIM</p>
            <h2>Claim Food with Verification Reason</h2>
            <p>Food donors require a clear purpose before approving requests to ensure food reaches people who truly need it.</p>
          </div>
          <button className="icon-button" onClick={onClose} aria-label="Close"><X size={18} /></button>
        </div>

        <div className="claim-item-summary">
          <div className="summary-left">
            <span className="summary-emoji">{donation.image_emoji || "🍲"}</span>
            <div>
              <h3>{donation.title}</h3>
              <p>Donated by: <b>{donation.donor_name || "Food Partner"}</b></p>
              <small><MapPin size={12} /> {donation.pickup_address}</small>
            </div>
          </div>
          <div className="summary-right">
            <span>Available to Claim:</span>
            <b>{donation.remaining_quantity} {donation.unit}</b>
          </div>
        </div>

        <form onSubmit={onSubmit} className="modal-form">
          <div className="form-grid">
            <div className="split-field span-2">
              <label>
                Quantity to Claim
                <div className="qty-input-wrap">
                  <input
                    required
                    type="number"
                    name="quantity"
                    min="0.1"
                    max={donation.remaining_quantity}
                    step="0.1"
                    value={quantity}
                    onChange={(e) => setQuantity(Number(e.target.value))}
                  />
                  <span className="qty-unit">{donation.unit}</span>
                  <button
                    type="button"
                    className="max-btn"
                    onClick={() => setQuantity(donation.remaining_quantity)}
                  >
                    Claim All
                  </button>
                </div>
              </label>

              <label>
                Beneficiary Organization Category
                <select name="beneficiary_type" required defaultValue="Children's Shelter / Orphanage">
                  <option value="Children's Shelter / Orphanage">Children's Shelter / Orphanage</option>
                  <option value="Community Kitchen / Langar">Community Kitchen / Langar</option>
                  <option value="Old Age & Senior Home">Old Age & Senior Home</option>
                  <option value="Hospital Patient Attendants">Hospital Patient Attendants</option>
                  <option value="Disaster Relief / Slum Feeding">Disaster Relief / Slum Feeding</option>
                  <option value="Other Community Need">Other Community Need</option>
                </select>
              </label>
            </div>

            <label className="span-2">
              <div className="label-with-hint">
                <span>Reason for Request / How will this food be utilized? <b>*Mandatory</b></span>
                <small>The food donor verifies this reason before approving.</small>
              </div>
              <textarea
                required
                name="claim_reason"
                minLength={10}
                rows={3}
                value={reason}
                onChange={(e) => setReason(e.target.value)}
                placeholder="Explain who will receive this food, portion counts, and distribution timing…"
              />
            </label>

            <div className="quick-reason-chips span-2">
              <span>Quick templates:</span>
              <div className="chip-list">
                {quickReasons.map((text, i) => (
                  <button
                    type="button"
                    key={i}
                    className="reason-chip"
                    onClick={() => setReason(text)}
                  >
                    {text}
                  </button>
                ))}
              </div>
            </div>

            <label>
              Organization / Contact Name
              <input
                required
                name="organization_name"
                defaultValue={user.name}
                placeholder="Name of your shelter or NGO"
              />
            </label>

            <label>
              Pickup Contact Mobile
              <input
                required
                name="contact_phone"
                defaultValue={user.phone || ""}
                placeholder="+91 98765 43210"
              />
            </label>

            <label className="span-2">
              Proposed Pickup Time
              <input
                required
                name="estimated_pickup"
                type="datetime-local"
                defaultValue={dateField(2)}
              />
            </label>
          </div>

          <div className="claim-modal-safety-note">
            <ShieldCheck size={16} />
            <span>Once approved, you will receive a Digital Pickup Pass with a unique claim code for collection.</span>
          </div>

          <div className="modal-footer">
            <button type="button" className="button ghost" onClick={onClose}>Cancel</button>
            <button type="submit" className="button primary">
              <Check size={16} /> Submit Claim to Donor
            </button>
          </div>
        </form>
      </section>
    </div>
  );
}

// ----------------- DIGITAL PICKUP PASS MODAL -----------------
function PickupPassModal({
  user,
  allocation,
  onClose,
  onAction,
}: {
  user: User;
  allocation: Allocation;
  onClose: () => void;
  onAction: (path: string, body?: unknown, success?: string) => Promise<void>;
}) {
  const isRecipient = user.role === "recipient" || user.id === allocation.recipient_id;
  const isDonor = user.role === "donor" || user.id === allocation.donor_id;

  return (
    <div className="modal-backdrop" onMouseDown={(e) => { if (e.target === e.currentTarget) onClose(); }}>
      <section className="modal pickup-pass-modal" role="dialog" aria-modal="true">
        <div className="pass-card">
          <div className="pass-header">
            <div className="pass-brand">
              <span className="brand-mark"><Leaf size={18} /></span>
              <span>FoodLoop <b>Pass</b></span>
            </div>
            <button className="icon-button light" onClick={onClose}><X size={18} /></button>
          </div>

          <div className="pass-code-banner">
            <small>OFFICIAL HANDOFF PASS</small>
            <h2>{allocation.pickup_code || `#PASS-${allocation.id}`}</h2>
            <StatusPill value={allocation.status} />
            {isDonor ? (
              <small style={{ marginTop: "6px", color: "var(--brand-forest)", fontWeight: 600 }}>
                Verify this code with recipient at food handoff.
              </small>
            ) : isRecipient ? (
              <small style={{ marginTop: "6px", color: "var(--brand-forest)", fontWeight: 600 }}>
                Present this code to donor staff at pickup.
              </small>
            ) : null}
          </div>

          <div className="pass-body">
            <div className="pass-row">
              <span>Food Item:</span>
              <b>{allocation.donation_title}</b>
            </div>
            <div className="pass-row">
              <span>Quantity:</span>
              <b>{allocation.quantity} {allocation.unit}</b>
            </div>
            <div className="pass-row">
              <span>Donated by:</span>
              <b>{allocation.donor_name || "Food Partner"}</b>
            </div>
            <div className="pass-row">
              <span>Pickup Location:</span>
              <b>{allocation.pickup_address || "Provided upon confirmation"}</b>
            </div>
            <div className="pass-row">
              <span>Claimed by:</span>
              <b>{allocation.organization}</b>
            </div>
            {allocation.claim_reason && (
              <div className="pass-reason-card">
                <small>Submitted Purpose / Reason:</small>
                <p>"{allocation.claim_reason}"</p>
              </div>
            )}
            {allocation.donor_notes && (
              <div className="pass-notes-card">
                <small>Donor Pickup Instructions:</small>
                <p>{allocation.donor_notes}</p>
              </div>
            )}
          </div>

          <div className="pass-actions">
            {allocation.status === "approved" && (
              <button
                className="button primary full"
                onClick={() => void onAction(`/allocations/${allocation.id}/event?event=collected`, undefined, "Food collected! Transport en route.")}
              >
                <PackageCheck size={16} /> Confirm Food Picked Up
              </button>
            )}
            {allocation.status === "collected" && (
              <button
                className="button primary full"
                onClick={() => void onAction(`/allocations/${allocation.id}/event?event=delivered`, undefined, "Delivery confirmed! Food successfully distributed.")}
              >
                <Check size={16} /> Confirm Delivered & Distributed
              </button>
            )}
            <button className="button ghost full" onClick={onClose}>
              Close Pass
            </button>
          </div>
        </div>
      </section>
    </div>
  );
}

// ----------------- CLAIMS PAGE (DIRECT WORKFLOW) -----------------
function ClaimsPage({
  user,
  allocations,
  onViewPass,
  onDonorDecision,
  onAction,
}: {
  user: User;
  allocations: Allocation[];
  onViewPass: (item: Allocation) => void;
  onDonorDecision: (allocationId: number, accept: boolean, notes?: string) => Promise<void>;
  onAction: (path: string, body?: unknown, success?: string) => Promise<void>;
}) {
  const isDonor = user.role === "donor";

  return (
    <>
      <PageHeading
        eyebrow="COMMUNITY FOOD CLAIMS"
        title={isDonor ? "Incoming Claims on Your Food" : "My Food Claims & Passes"}
        detail={
          isDonor
            ? "Review reasons submitted by shelters and NGOs wanting your surplus food. Approve requests to issue pickup passes."
            : "Track your food claims, submitted reasons, donor approvals, and digital collection passes."
        }
      />

      <div className="claims-list">
        {allocations.map((item) => {
          const isPending = item.status === "pending_donor";
          const isApproved = item.status === "approved" || item.status === "collected" || item.status === "delivered";

          return (
            <article className={`claim-card ${isPending ? "pending-card" : ""}`} key={item.id}>
              <div className="claim-card-top">
                <div className="claim-title-group">
                  <span className="claim-badge">#{item.pickup_code || item.id}</span>
                  <div>
                    <h3>{item.donation_title}</h3>
                    <small>
                      {isDonor ? `Claimed by: ${item.organization}` : `Donor: ${item.donor_name || "Food Partner"}`}
                    </small>
                  </div>
                </div>
                <StatusPill value={item.status} />
              </div>

              <div className="claim-reason-highlight">
                <small><HandHeart size={14} /> Submitted Reason / Purpose:</small>
                <p>"{item.claim_reason || "Direct distribution to community members in need."}"</p>
                {item.beneficiary_type && (
                  <span className="beneficiary-pill">{item.beneficiary_type}</span>
                )}
              </div>

              <div className="claim-meta-row">
                <span><b>{item.quantity} {item.unit}</b> requested</span>
                {item.pickup_address && <span><MapPin size={13} /> {item.pickup_address}</span>}
                {item.contact_phone && <span><Phone size={13} /> {item.contact_phone}</span>}
              </div>

              {item.donor_notes && (
                <div className="claim-donor-notes">
                  <small>Donor Notes:</small>
                  <p>{item.donor_notes}</p>
                </div>
              )}

              <div className="claim-card-actions">
                {isDonor && isPending && (
                  <div className="donor-decision-buttons">
                    <button
                      className="button primary"
                      onClick={() => void onDonorDecision(item.id, true)}
                    >
                      <Check size={16} /> Approve Request & Issue Pass
                    </button>
                    <button
                      className="button ghost danger"
                      onClick={() => void onDonorDecision(item.id, false, "Food already claimed or capacity unavailable.")}
                    >
                      <X size={16} /> Decline
                    </button>
                  </div>
                )}

                {isApproved && (
                  <button className="button subtle" onClick={() => onViewPass(item)}>
                    <QrCode size={16} /> View Digital Pickup Pass ({item.pickup_code})
                  </button>
                )}

                {item.status === "approved" && (
                  <button
                    className="button mini primary"
                    onClick={() => void onAction(`/allocations/${item.id}/event?event=collected`, undefined, "Pickup recorded!")}
                  >
                    Mark Picked Up
                  </button>
                )}

                {item.status === "collected" && (
                  <button
                    className="button mini primary"
                    onClick={() => void onAction(`/allocations/${item.id}/event?event=delivered`, undefined, "Delivery confirmed!")}
                  >
                    Confirm Delivery
                  </button>
                )}
              </div>
            </article>
          );
        })}

        {allocations.length === 0 && (
          <div className="panel empty-state">
            <HandHeart size={26} />
            <b>No claims yet</b>
            <p>
              {isDonor
                ? "When shelters or NGOs claim your surplus food or near-expiry items with a verified reason, they will appear here."
                : "Browse the Live Food Hub to claim surplus meals or near-expiry grocery items."}
            </p>
          </div>
        )}
      </div>
    </>
  );
}

// ----------------- OVERVIEW PAGE -----------------
function Overview({
  user,
  dashboard,
  donations,
  matches,
  allocations,
  onNavigate,
  onOpenDonation,
  onViewPass,
  onDonorDecision,
}: {
  user: User;
  dashboard: Dashboard | null;
  donations: Donation[];
  matches: Match[];
  allocations: Allocation[];
  onNavigate: (page: Page) => void;
  onOpenDonation: () => void;
  onViewPass: (item: Allocation) => void;
  onDonorDecision: (id: number, accept: boolean) => Promise<void>;
}) {
  const pendingDonorClaims = allocations.filter((a) => a.status === "pending_donor");

  const stats = user.role === "donor"
    ? [
        { label: "Active Listings", value: dashboard?.active_donations ?? donations.length, suffix: "offers live", icon: Boxes, tone: "green" },
        { label: "Incoming Claims", value: pendingDonorClaims.length, suffix: "need review", icon: HandHeart, tone: "gold" },
        { label: "Handoffs in Transit", value: allocations.filter((item) => ["approved", "collected"].includes(item.status)).length, suffix: "active passes", icon: Truck, tone: "blue" },
        { label: "Food Saved & Delivered", value: dashboard?.delivered_quantity ?? 0, suffix: "confirmed qty", icon: PackageCheck, tone: "sage" },
      ]
    : user.role === "recipient"
      ? [
          { label: "Food Hub Available", value: donations.filter((d) => d.status === "available").length, suffix: "offers ready", icon: Sparkles, tone: "green" },
          { label: "My Active Claims", value: allocations.filter((a) => ["pending_donor", "approved"].includes(a.status)).length, suffix: "in progress", icon: HandHeart, tone: "gold" },
          { label: "Pickup Passes", value: allocations.filter((a) => a.status === "approved").length, suffix: "ready to collect", icon: QrCode, tone: "blue" },
          { label: "Received & Distributed", value: dashboard?.delivered_quantity ?? 0, suffix: "confirmed qty", icon: PackageCheck, tone: "sage" },
        ]
      : [
          { label: "Available Food Offers", value: dashboard?.active_donations ?? donations.length, suffix: "listings live", icon: Boxes, tone: "green" },
          { label: "Open Community Demands", value: dashboard?.active_requests ?? 0, suffix: "recipient requests", icon: ClipboardList, tone: "gold" },
          { label: "AI Feasible Matches", value: matches.length, suffix: "ranked recommendations", icon: Sparkles, tone: "blue" },
          { label: "Delivered & Saved", value: dashboard?.completed_deliveries ?? 0, suffix: "confirmed handoffs", icon: PackageCheck, tone: "sage" },
        ];

  const action = user.role === "donor"
    ? <button className="button primary" onClick={onOpenDonation}><Plus size={16} />Post Surplus Food</button>
    : user.role === "recipient"
      ? <button className="button primary" onClick={() => onNavigate("explore")}><Sparkles size={16} />Browse Food Hub</button>
      : <button className="button primary" onClick={() => onNavigate("matching")}><Sparkles size={16} />Review AI Matches</button>;

  const priorities = donations
    .filter((item) => item.status === "available")
    .sort((a, b) => new Date(a.usable_until).getTime() - new Date(b.usable_until).getTime())
    .slice(0, 4);

  return (
    <>
      <PageHeading
        eyebrow={`${prettyRole(user.role)} Operations`}
        title={user.role === "donor" ? "Your Food Rescue Workspace" : user.role === "recipient" ? "Community Food Supply" : "City Food Rescue Operations"}
        detail="Real-time surplus food tracking, verified claim reasons, and transparent community handoffs."
        action={action}
      />

      {user.role === "donor" && pendingDonorClaims.length > 0 && (
        <section className="offer-banner urgent-attention">
          <span className="offer-glyph"><HandHeart size={20} /></span>
          <div>
            <b>{pendingDonorClaims.length} food claim{pendingDonorClaims.length === 1 ? "" : "s"} waiting for your review</b>
            <p>Shelters and community partners have submitted reasons to receive your food.</p>
          </div>
          <button className="button primary" onClick={() => onNavigate("claims")}>
            Review Reasons & Approve <ArrowRight size={14} />
          </button>
        </section>
      )}

      <div className="metric-grid">
        {stats.map(({ label, value, suffix, icon: Icon, tone }) => (
          <article className="metric-card" key={label}>
            <div className={`metric-icon ${tone}`}><Icon size={18} /></div>
            <p>{label}</p>
            <div className="metric-value">
              {typeof value === "number" && !Number.isInteger(value) ? value.toFixed(1) : value}
              <small>{suffix}</small>
            </div>
          </article>
        ))}
      </div>

      <div className="notice-line">
        <ShieldCheck size={15} />
        <span>FoodLoop AI guarantees human verification on every handoff. Donors evaluate claim reasons before collection.</span>
        <span className="live-mark"><i /> LIVE DATABASE RECORDS</span>
      </div>

      <section className="overview-grid">
        <article className="panel urgency-panel">
          <div className="panel-heading">
            <div>
              <p className="eyebrow">EXPIRY ALERT</p>
              <h2>Time-Sensitive Food</h2>
            </div>
            <button className="text-button" onClick={() => onNavigate("explore")}>
              Explore all <ArrowRight size={14} />
            </button>
          </div>

          {priorities.length ? (
            <div className="priority-list">
              {priorities.map((item) => {
                const hoursLeft = Math.max(0, Math.ceil((new Date(item.usable_until).getTime() - Date.now()) / 3600000));
                const isSupermarket = item.donor_type === "supermarket_dmart";

                return (
                  <div className="priority-row" key={item.id}>
                    <span className="priority-icon">
                      {isSupermarket ? <Store size={16} /> : <Utensils size={16} />}
                    </span>
                    <div className="priority-main">
                      <b>{item.title}</b>
                      <span>{item.remaining_quantity} {item.unit} · {isSupermarket ? "Supermarket / D-Mart" : "Restaurant Surplus"}</span>
                    </div>
                    <span className={hoursLeft <= 6 ? "time-left urgent" : "time-left"}>
                      <Clock3 size={13} /> {hoursLeft < 1 ? "under 1h" : `${hoursLeft}h left`}
                    </span>
                  </div>
                );
              })}
            </div>
          ) : (
            <EmptyState icon={<Boxes size={22} />} title="No urgent items right now" detail="Active listings ordered by expiration countdown will appear here." />
          )}
        </article>

        <article className="panel category-panel">
          <div className="panel-heading">
            <div>
              <p className="eyebrow">FOOD MIX</p>
              <h2>Saved by Category</h2>
            </div>
            <button className="mini-select" onClick={() => onNavigate("impact")}>
              View analytics <ArrowRight size={13} />
            </button>
          </div>
          <div className="category-bars">
            {Object.entries(dashboard?.category_distribution ?? {}).length > 0 ? (
              Object.entries(dashboard?.category_distribution ?? {}).map(([key, val], idx) => (
                <div className="bar-row" key={key}>
                  <span>{categoryLabels[key] ?? readable(key)}</span>
                  <div className="bar-track">
                    <i style={{ width: `${Math.min(100, Math.max(8, val * 4))}%`, backgroundColor: colors[idx % colors.length] }} />
                  </div>
                  <b>{val}</b>
                </div>
              ))
            ) : (
              <p className="inline-empty">Category distribution will update as food is donated.</p>
            )}
          </div>
        </article>
      </section>

      <section className="panel table-panel">
        <div className="panel-heading">
          <div>
            <p className="eyebrow">RECENT CLAIMS & ACTIVITY</p>
            <h2>Food Handoff Records</h2>
          </div>
          <button className="text-button" onClick={() => onNavigate("claims")}>
            All claims <ArrowRight size={14} />
          </button>
        </div>

        {allocations.length ? (
          <div className="table-scroll">
            <table>
              <thead>
                <tr>
                  <th>FOOD ITEM</th>
                  <th>RECIPIENT / PURPOSE</th>
                  <th>QTY</th>
                  <th>STATUS</th>
                  <th>PASS / ACTIONS</th>
                </tr>
              </thead>
              <tbody>
                {allocations.slice(0, 5).map((item) => (
                  <tr key={item.id}>
                    <td>
                      <b>{item.donation_title}</b>
                      <small className="cell-sub">{item.pickup_address}</small>
                    </td>
                    <td>
                      <b>{item.organization}</b>
                      {item.claim_reason && (
                        <small className="cell-sub quote-sub">"{item.claim_reason.slice(0, 48)}…"</small>
                      )}
                    </td>
                    <td><b>{item.quantity}</b> {item.unit}</td>
                    <td><StatusPill value={item.status} /></td>
                    <td>
                      {item.pickup_code ? (
                        <button className="button mini subtle" onClick={() => onViewPass(item)}>
                          <QrCode size={13} /> Pass: {item.pickup_code}
                        </button>
                      ) : user.role === "donor" && item.status === "pending_donor" ? (
                        <button className="button mini primary" onClick={() => void onDonorDecision(item.id, true)}>
                          Approve
                        </button>
                      ) : (
                        "—"
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <EmptyState icon={<HandHeart size={22} />} title="No claims recorded yet" detail="Food claims and approvals will show here in real time." />
        )}
      </section>
    </>
  );
}

function EmptyState({ icon, title, detail, action }: { icon: React.ReactNode; title: string; detail: string; action?: React.ReactNode }) {
  return (
    <div className="empty-state">
      <span className="empty-icon">{icon}</span>
      <b>{title}</b>
      <p>{detail}</p>
      {action}
    </div>
  );
}

// ----------------- DONATIONS PAGE -----------------
function DonationsPage({
  user,
  donations,
  onAdd,
  onClaim,
}: {
  user: User;
  donations: Donation[];
  onAdd: () => void;
  onClaim: (d: Donation) => void;
}) {
  const [filterType, setFilterType] = useState<"all" | "restaurant_stall" | "supermarket_dmart">("all");
  const filtered = donations.filter((d) => filterType === "all" || d.donor_type === filterType);

  return (
    <>
      <PageHeading
        eyebrow="INVENTORY MANAGEMENT"
        title={user.role === "donor" ? "My Food Offers" : "Available Food Offers"}
        detail="Manage items posted for rescue, verified storage standards, and collection deadlines."
        action={user.role === "donor" ? <button className="button primary" onClick={onAdd}><Plus size={16} />Post Surplus Food</button> : undefined}
      />

      <div className="explore-toolbar">
        <div className="explore-type-tabs">
          <button className={`type-tab ${filterType === "all" ? "active" : ""}`} onClick={() => setFilterType("all")}>
            All Items ({donations.length})
          </button>
          <button className={`type-tab ${filterType === "restaurant_stall" ? "active" : ""}`} onClick={() => setFilterType("restaurant_stall")}>
            🍲 Restaurant & Stall
          </button>
          <button className={`type-tab ${filterType === "supermarket_dmart" ? "active" : ""}`} onClick={() => setFilterType("supermarket_dmart")}>
            🏬 Supermarket / D-Mart
          </button>
        </div>
      </div>

      <section className="panel list-panel">
        {filtered.length ? (
          <div className="table-scroll">
            <table>
              <thead>
                <tr>
                  <th>FOOD & TYPE</th>
                  <th>CATEGORY</th>
                  <th>REMAINING</th>
                  <th>STORAGE</th>
                  <th>USABLE UNTIL</th>
                  <th>STATUS</th>
                  {user.role === "recipient" && <th />}
                </tr>
              </thead>
              <tbody>
                {filtered.map((item) => (
                  <tr key={item.id}>
                    <td>
                      <b>{item.title}</b>
                      <small className="cell-sub">
                        {item.donor_type === "supermarket_dmart" ? "🏬 Supermarket / D-Mart" : "🍲 Restaurant / Stall"} · {item.pickup_address}
                      </small>
                    </td>
                    <td>{categoryLabels[item.category] ?? readable(item.category)}</td>
                    <td>
                      <b>{item.remaining_quantity}</b> {item.unit}
                      <small className="cell-sub">of {item.quantity} listed</small>
                    </td>
                    <td>
                      <span className="handling-tag">{readable(item.storage)}</span>
                      {item.dietary && <small className="cell-sub">{item.dietary.toUpperCase()}</small>}
                    </td>
                    <td>
                      {new Date(item.usable_until).toLocaleString([], { month: "short", day: "numeric", hour: "2-digit", minute: "2-digit" })}
                    </td>
                    <td><StatusPill value={item.status} /></td>
                    {user.role === "recipient" && (
                      <td>
                        <button className="button mini primary" onClick={() => onClaim(item)}>
                          Claim
                        </button>
                      </td>
                    )}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <EmptyState
            icon={<Boxes size={24} />}
            title="No food listings found"
            detail="Post your surplus food or near-expiry items to start connecting with the community."
            action={user.role === "donor" ? <button className="button subtle" onClick={onAdd}><Plus size={15} />Post Food</button> : undefined}
          />
        )}
      </section>
    </>
  );
}

// ----------------- REQUESTS PAGE -----------------
function RequestsPage({
  role,
  requests,
  onAdd,
}: {
  role: Role;
  requests: FoodRequest[];
  onAdd: () => void;
}) {
  return (
    <>
      <PageHeading
        eyebrow="COMMUNITY DEMAND"
        title={role === "recipient" ? "My Organization Requests" : "Community Food Requests"}
        detail="Broad community dietary requirements shared by partner shelters, orphanages, and disaster relief programs."
        action={role === "recipient" ? <button className="button primary" onClick={onAdd}><Plus size={16} />New Food Request</button> : undefined}
      />

      <div className="request-grid">
        {requests.map((item) => (
          <article className="request-card" key={item.id}>
            <div className="request-card-top">
              <span className={`urgency ${item.urgency >= 8 ? "high" : item.urgency >= 5 ? "medium" : "low"}`}>
                {item.urgency >= 8 ? "HIGH" : item.urgency >= 5 ? "MEDIUM" : "STANDARD"} URGENCY
              </span>
              <StatusPill value={item.status} />
            </div>
            <h2>{item.organization}</h2>
            <div className="request-quantity">
              <b>{item.unmet_quantity}</b>
              <span>{item.unit} needed <i>of {item.requested_quantity} requested</i></span>
            </div>
            <div className="request-meta">
              <span><MapPin size={14} /> {item.delivery_address}</span>
              <span><Clock3 size={14} /> Needed by {new Date(item.needed_by).toLocaleString([], { month: "short", day: "numeric", hour: "2-digit" })}</span>
            </div>
            <div className="request-tags">
              {item.categories.map((cat) => (
                <span key={cat}>{categoryLabels[cat] ?? readable(cat)}</span>
              ))}
            </div>
          </article>
        ))}
      </div>

      {!requests.length && (
        <section className="panel">
          <EmptyState
            icon={<ClipboardList size={22} />}
            title="No open requests"
            detail={role === "recipient" ? "Post a request to state your community's food requirement." : "Recipient demands will appear here when posted."}
            action={role === "recipient" ? <button className="button subtle" onClick={onAdd}><Plus size={15} />Create Request</button> : undefined}
          />
        </section>
      )}
    </>
  );
}

// ----------------- MATCHING PAGE -----------------
function MatchingPage({
  matches,
  rejections,
  donations,
  onPropose,
}: {
  matches: Match[];
  rejections: MatchResult["rejected"];
  donations: Donation[];
  onPropose: (match: Match) => Promise<void>;
}) {
  const [selected, setSelected] = useState<Match | null>(null);

  return (
    <>
      <PageHeading
        eyebrow="COORDINATOR DISPATCH"
        title="AI Match Desk"
        detail="Constraint-checked deterministic matching between surplus donors and recipient requests."
        action={<span className="algorithm-label"><Sparkles size={15} />CONSTRAINT SOLVER</span>}
      />
      <div className="match-layout">
        <div className="match-list">
          {matches.map((match, idx) => {
            const donation = donations.find((d) => d.id === match.donation_id);
            return (
              <article
                className={`match-card ${selected?.donation_id === match.donation_id && selected.request_id === match.request_id ? "active" : ""}`}
                key={`${match.donation_id}-${match.request_id}`}
                onClick={() => setSelected(match)}
              >
                <div className="match-card-top">
                  <span className="rank-number">{String(idx + 1).padStart(2, "0")}</span>
                  <span className="match-score">{match.score}<small> SCORE</small></span>
                  <span className="match-distance"><MapPin size={13} /> {match.distance_km} km</span>
                </div>
                <h3>{donation?.title ?? `Donation #${match.donation_id}`}</h3>
                <p className="match-to">To <b>{match.organization}</b></p>
                <div className="match-stats">
                  <span><b>{match.quantity} {match.unit}</b><small>proposed</small></span>
                  <span><b>{donation ? readable(donation.storage) : "Verified"}</b><small>handling</small></span>
                </div>
                <p className="match-explanation"><Sparkles size={14} /> {match.explanation}</p>
                <div className="match-actions">
                  <button className="button primary" onClick={(e) => { e.stopPropagation(); void onPropose(match); }}>
                    <HandHeart size={15} /> Dispatch Offer
                  </button>
                  <button className="button ghost" onClick={(e) => { e.stopPropagation(); setSelected(match); }}>
                    Details
                  </button>
                </div>
              </article>
            );
          })}
          {!matches.length && (
            <div className="panel empty-state">
              <Sparkles size={22} />
              <b>No automated pairings available</b>
              <p>Direct claiming on the Live Food Hub is always available for immediate needs.</p>
            </div>
          )}
        </div>

        <aside className="panel match-detail">
          <p className="eyebrow">MATCH LOGIC</p>
          <h2>{selected ? "Pairing Breakdown" : "System Verification"}</h2>
          {selected ? (
            <>
              <p className="match-detail-copy">{selected.explanation}</p>
              <div className="score-line">
                <span>Recommendation Score</span>
                <b>{selected.score} / 100</b>
              </div>
              <ul className="constraint-list">
                <li><Check size={14} /> Category match verified</li>
                <li><Check size={14} /> Storage capability compatible</li>
                <li><Check size={14} /> Allergen exclusions checked</li>
                <li><Check size={14} /> Time deadline valid</li>
              </ul>
            </>
          ) : (
            <div className="weight-list">
              <span>Perishability & Time <b>35%</b></span>
              <span>Quantity suitability <b>20%</b></span>
              <span>Local Distance <b>15%</b></span>
              <span>Urgency <b>15%</b></span>
              <span>Fairness History <b>15%</b></span>
            </div>
          )}
          {Object.keys(rejections).length > 0 && (
            <p style={{ marginTop: "1rem", fontSize: "0.8rem", color: "var(--text-muted)" }}>
              ℹ️ {Object.keys(rejections).length} candidate pairings filtered out by allergen, storage, or distance constraints.
            </p>
          )}
        </aside>
      </div>
    </>
  );
}

// ----------------- HANDOFFS PAGE -----------------
function HandoffsPage({
  allocations,
  donations,
  requests,
  onViewPass,
}: {
  allocations: Allocation[];
  donations: Donation[];
  requests: FoodRequest[];
  onViewPass: (item: Allocation) => void;
}) {
  const [filter, setFilter] = useState("all");
  const filtered = allocations.filter((item) => filter === "all" || item.status === filter);
  const markers = [
    ...donations.filter((d) => d.status === "available").map((d) => ({ kind: "donor", name: d.title, lat: d.latitude, lon: d.longitude })),
    ...requests.filter((r) => r.status === "open").map((r) => ({ kind: "recipient", name: r.organization, lat: r.latitude, lon: r.longitude })),
  ];
  const center: [number, number] = markers.length ? [markers[0].lat, markers[0].lon] : [12.9716, 77.5946];

  return (
    <>
      <PageHeading
        eyebrow="LOGISTICS & HANDOFFS"
        title="Active Food Deliveries"
        detail="Real-time map overview and status tracking for all food collection passes."
      />
      <section className="panel map-panel">
        <MapView markers={markers} center={center} />
      </section>

      <section className="panel table-panel">
        <div className="panel-heading">
          <div>
            <p className="eyebrow">PASSES & LIFECYCLE</p>
            <h2>Pickup & Distribution Progress</h2>
          </div>
          <div className="filter-select">
            <span>Status:</span>
            <select value={filter} onChange={(e) => setFilter(e.target.value)}>
              <option value="all">All Statuses</option>
              {Object.entries(statusLabels).map(([k, v]) => (
                <option key={k} value={k}>{v}</option>
              ))}
            </select>
          </div>
        </div>

        {filtered.length ? (
          <div className="table-scroll">
            <table>
              <thead>
                <tr>
                  <th>FOOD ITEM</th>
                  <th>RECIPIENT</th>
                  <th>QTY</th>
                  <th>STATUS</th>
                  <th>PASS / NEXT STEP</th>
                </tr>
              </thead>
              <tbody>
                {filtered.map((item) => (
                  <tr key={item.id}>
                    <td><b>{item.donation_title}</b></td>
                    <td>{item.organization}</td>
                    <td>{item.quantity} {item.unit}</td>
                    <td><StatusPill value={item.status} /></td>
                    <td>
                      {item.pickup_code && (
                        <button className="button mini subtle" onClick={() => onViewPass(item)}>
                          <QrCode size={13} /> {item.pickup_code}
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <EmptyState icon={<Truck size={22} />} title="No handoffs found" detail="Active deliveries appear here once approved." />
        )}
      </section>
    </>
  );
}

function MapView({ markers, center }: { markers: { kind: string; name: string; lat: number; lon: number }[]; center: [number, number] }) {
  const [components, setComponents] = useState<{ MapContainer: React.ComponentType<any>; TileLayer: React.ComponentType<any>; CircleMarker: React.ComponentType<any>; Popup: React.ComponentType<any> } | null>(null);

  useEffect(() => {
    let active = true;
    void import("react-leaflet").then((mod) => { if (active) setComponents(mod); });
    return () => { active = false; };
  }, []);

  if (!components) return <div className="map-loading"><MapPin size={19} /> Loading interactive map…</div>;
  const { MapContainer, TileLayer, CircleMarker, Popup } = components;

  return (
    <MapContainer center={center} zoom={12} scrollWheelZoom={false} className="leaflet-map">
      <TileLayer attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>' url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png" />
      {markers.map((m, i) => (
        <CircleMarker key={`${m.kind}-${m.name}-${i}`} center={[m.lat, m.lon]} radius={8} pathOptions={{ color: m.kind === "donor" ? "#2d6a4f" : "#e07a5f", fillOpacity: 0.9 }}>
          <Popup><b>{m.name}</b><br />{m.kind === "donor" ? "Food Donor Location" : "Community Recipient"}</Popup>
        </CircleMarker>
      ))}
    </MapContainer>
  );
}

// ----------------- IMPACT PAGE -----------------
function ImpactPage({ dashboard, chartData, onExport }: { dashboard: Dashboard | null; chartData: { name: string; value: number }[]; onExport: () => void }) {
  return (
    <>
      <PageHeading
        eyebrow="MEASURED IMPACT"
        title="Zero Waste Community Impact"
        detail="Transparent, database-backed tracking of food rescued from restaurants and supermarkets."
        action={<button className="button secondary" onClick={onExport}><ArrowDownToLine size={16} /> Export CSV</button>}
      />

      <div className="impact-grid">
        <article className="impact-stat">
          <p>Total Food Listed</p>
          <b>{dashboard?.listed_quantity ?? 0} <small>recorded qty</small></b>
          <span>From restaurants, stalls, & D-Marts</span>
        </article>
        <article className="impact-stat delivered">
          <p>Delivered & Distributed</p>
          <b>{dashboard?.delivered_quantity ?? 0} <small>recipient verified</small></b>
          <span>{dashboard?.completed_deliveries ?? 0} confirmed meals / handoffs</span>
        </article>
        <article className="impact-stat">
          <p>Active In Handoff</p>
          <b>{dashboard?.pending_matches ?? 0} <small>passes active</small></b>
          <span>Approved & in transit</span>
        </article>
        <article className="impact-stat">
          <p>Community Demand</p>
          <b>{dashboard?.unmet_demand ?? 0} <small>unmet qty</small></b>
          <span>Open requests from shelters</span>
        </article>
      </div>

      <section className="analytics-grid">
        <article className="panel chart-panel">
          <div className="panel-heading">
            <div>
              <p className="eyebrow">DISTRIBUTION</p>
              <h2>Rescued Food by Category</h2>
            </div>
          </div>
          {chartData.length ? (
            <div className="chart-wrap">
              <ResponsiveContainer width="100%" height="100%">
                <AreaChart data={chartData} margin={{ top: 10, right: 12, bottom: 0, left: -14 }}>
                  <defs>
                    <linearGradient id="foodGrad" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="0%" stopColor="#2d6a4f" stopOpacity={0.3} />
                      <stop offset="100%" stopColor="#2d6a4f" stopOpacity={0.02} />
                    </linearGradient>
                  </defs>
                  <CartesianGrid vertical={false} stroke="#e4e7df" />
                  <XAxis dataKey="name" axisLine={false} tickLine={false} tick={{ fill: "#6a736a", fontSize: 11 }} />
                  <YAxis axisLine={false} tickLine={false} tick={{ fill: "#6a736a", fontSize: 11 }} />
                  <Tooltip contentStyle={{ borderRadius: 8, border: "1px solid #d8ddd0" }} />
                  <Area type="monotone" dataKey="value" name="Rescued Quantity" stroke="#2d6a4f" strokeWidth={2.5} fill="url(#foodGrad)" />
                </AreaChart>
              </ResponsiveContainer>
            </div>
          ) : (
            <EmptyState icon={<Activity size={22} />} title="Charts will display once items are donated" detail="Live breakdown by category." />
          )}
        </article>

        <article className="panel chart-panel pie-panel">
          <div className="panel-heading">
            <div>
              <p className="eyebrow">FOOD MIX</p>
              <h2>Category Composition</h2>
            </div>
          </div>
          {chartData.length ? (
            <>
              <div className="pie-wrap">
                <ResponsiveContainer width="100%" height="100%">
                  <PieChart>
                    <Pie data={chartData} dataKey="value" nameKey="name" innerRadius={55} outerRadius={80} paddingAngle={3} stroke="none">
                      {chartData.map((e, idx) => <Cell key={e.name} fill={colors[idx % colors.length]} />)}
                    </Pie>
                    <Tooltip />
                  </PieChart>
                </ResponsiveContainer>
              </div>
              <div className="pie-legend">
                {chartData.map((e, idx) => (
                  <span key={e.name}><i style={{ background: colors[idx % colors.length] }} />{e.name}</span>
                ))}
              </div>
            </>
          ) : (
            <EmptyState icon={<Activity size={22} />} title="No data recorded yet" detail="Populates from active records." />
          )}
        </article>
      </section>
    </>
  );
}

// ----------------- SETTINGS PAGE -----------------
function SettingsPage({ user, onSignOut }: { user: User; onSignOut: () => void }) {
  return (
    <>
      <PageHeading eyebrow="PROFILE" title="Account & Organization" detail="Manage your organization profile, role credentials, and notification settings." />
      <section className="panel settings-panel">
        <div className="settings-avatar">{user.name.slice(0, 1).toUpperCase()}</div>
        <div className="settings-details">
          <h2>{user.name}</h2>
          <span>{user.email}</span>
          <div className="settings-role-tags">
            <span className="role-pill"><ShieldCheck size={14} /> {prettyRole(user.role)}</span>
            {user.org_type && <span className="org-pill">{readable(user.org_type)}</span>}
            {user.city && <span className="city-pill"><MapPin size={13} /> {user.city}</span>}
            {user.phone && <span className="phone-pill"><Phone size={13} /> {user.phone}</span>}
          </div>
        </div>
      </section>

      <section className="panel security-panel">
        <div>
          <p className="eyebrow">SESSION & SECURITY</p>
          <h2>Active Session</h2>
          <p>Authenticated securely with encrypted JWT token. To switch to a different test account or log out, use the button below.</p>
        </div>
        <button className="button danger-outline" onClick={onSignOut}><LogOut size={16} /> Sign Out</button>
      </section>
    </>
  );
}

// ----------------- FORM MODAL (POST FOOD OR REQUEST) -----------------
function FormModal({
  kind,
  user,
  onClose,
  onDonation,
  onRequest,
}: {
  kind: "donation" | "request";
  user: User;
  onClose: () => void;
  onDonation: (e: FormEvent<HTMLFormElement>) => void;
  onRequest: (e: FormEvent<HTMLFormElement>) => void;
}) {
  const isDonation = kind === "donation";
  const [donorType, setDonorType] = useState<"restaurant_stall" | "supermarket_dmart">("restaurant_stall");

  return (
    <div className="modal-backdrop" onMouseDown={(e) => { if (e.target === e.currentTarget) onClose(); }}>
      <section className="modal form-modal" role="dialog" aria-modal="true">
        <div className="modal-heading">
          <div>
            <p className="eyebrow">{isDonation ? "FOOD DONATION WIZARD" : "COMMUNITY DEMAND"}</p>
            <h2>{isDonation ? "Post Surplus or Near-Expiry Food" : "Create Community Food Request"}</h2>
            <p>{isDonation ? "Share verified details so community organizations can request and collect this food safely." : "Post your shelter's food requirements to receive matching donations."}</p>
          </div>
          <button className="icon-button" onClick={onClose}><X size={18} /></button>
        </div>

        <form onSubmit={isDonation ? onDonation : onRequest} className="modal-form">
          <div className="form-grid">
            {isDonation ? (
              <>
                <div className="span-2 donor-type-selector">
                  <span className="input-heading">Select Source Type:</span>
                  <div className="type-toggle-group">
                    <button
                      type="button"
                      className={`type-toggle-btn ${donorType === "restaurant_stall" ? "active" : ""}`}
                      onClick={() => setDonorType("restaurant_stall")}
                    >
                      <Utensils size={16} />
                      <div>
                        <b>Restaurant / Food Stall / Caterer</b>
                        <small>Fresh cooked surplus, buffet food, hot meals</small>
                      </div>
                    </button>
                    <button
                      type="button"
                      className={`type-toggle-btn ${donorType === "supermarket_dmart" ? "active" : ""}`}
                      onClick={() => setDonorType("supermarket_dmart")}
                    >
                      <Store size={16} />
                      <div>
                        <b>Supermarket / D-Mart / Grocery</b>
                        <small>Near-expiry packaged goods, dairy, bread, fruits</small>
                      </div>
                    </button>
                  </div>
                  <input type="hidden" name="donor_type" value={donorType} />
                </div>

                <label className="span-2">
                  Food Item Title
                  <input
                    required
                    name="title"
                    minLength={3}
                    placeholder={donorType === "restaurant_stall" ? "e.g. Veg Pulao & Paneer Curry, 30 portions" : "e.g. Amul Milk 500ml / Britannia Bread / Canned Goods"}
                  />
                </label>

                <label className="span-2">
                  Description & Details
                  <textarea
                    name="description"
                    rows={2}
                    placeholder={donorType === "restaurant_stall" ? "Fresh from lunch buffet, cooked at 1 PM, kept hot in insulated containers…" : "Near-expiry retail packs (best before tomorrow), sealed manufacturer packaging…"}
                  />
                </label>

                <label>
                  Category
                  <select name="category" required defaultValue={donorType === "restaurant_stall" ? "prepared_meals" : "packaged"}>
                    {categoryOptions.map(([val, label]) => (
                      <option key={val} value={val}>{label}</option>
                    ))}
                  </select>
                </label>

                <label>
                  Dietary Classification
                  <select name="dietary" defaultValue="veg">
                    <option value="veg">🥗 Pure Vegetarian</option>
                    <option value="non_veg">🍗 Non-Vegetarian</option>
                    <option value="vegan">🌱 100% Vegan</option>
                    <option value="halal">حلال Halal</option>
                  </select>
                </label>

                <div className="split-field">
                  <label>
                    Quantity Available
                    <input required type="number" name="quantity" min="0.1" step="0.1" defaultValue="20" />
                  </label>
                  <label>
                    Unit
                    <select name="unit" defaultValue={donorType === "restaurant_stall" ? "portions" : "items"}>
                      <option value="portions">portions</option>
                      <option value="items">items / packets</option>
                      <option value="kg">kg</option>
                    </select>
                  </label>
                </div>

                <label>
                  Storage Requirement
                  <select name="storage" defaultValue={donorType === "restaurant_stall" ? "hot_holding" : "ambient"}>
                    {storageOptions.map((v) => <option key={v} value={v}>{readable(v)}</option>)}
                  </select>
                </label>

                {donorType === "supermarket_dmart" && (
                  <>
                    <label>
                      Original Retail Price (₹)
                      <input type="number" name="original_price" min="0" step="1" placeholder="e.g. 50 (Listed as Free)" />
                    </label>
                    <label>
                      Official Expiry / Best-Before Date
                      <input required name="expiry_date" type="datetime-local" defaultValue={dateField(36)} />
                    </label>
                  </>
                )}

                <label className="span-2">
                  Known Allergens
                  <input name="allergens" placeholder="e.g. dairy, nuts, gluten, peanuts (leave blank if none)" />
                </label>

                <label className="span-2">
                  Pickup Address
                  <input required name="pickup_address" defaultValue={user.city ? `${user.name}, ${user.city}` : ""} placeholder="Full address for collection" />
                </label>

                <div className="split-field span-2">
                  <label>
                    Pickup Starts
                    <input required name="pickup_start" type="datetime-local" defaultValue={dateField(0.5)} />
                  </label>
                  <label>
                    Pickup Ends
                    <input required name="pickup_end" type="datetime-local" defaultValue={dateField(4)} />
                  </label>
                </div>

                <label className="span-2">
                  Safe Usable Until (Deadline)
                  <input required name="usable_until" type="datetime-local" defaultValue={dateField(6)} />
                </label>

                <label className="span-2">
                  Packaging Notes
                  <input name="packaging" placeholder="e.g. Sealed containers, disposable meal boxes, retail crates" />
                </label>

                <input type="hidden" name="latitude" value="12.9716" />
                <input type="hidden" name="longitude" value="77.5946" />

                <label className="checkline span-2">
                  <input required type="checkbox" name="safety_verified" defaultChecked />
                  <span>I confirm this food has been handled in hygienic conditions and safe for consumption within the specified timeframe.</span>
                </label>
              </>
            ) : (
              <>
                <label className="span-2">
                  Organization Name
                  <input required name="organization" defaultValue={user.name} placeholder="Name of your shelter or kitchen" />
                </label>

                <div className="span-2">
                  <span className="input-heading">Requested Categories</span>
                  <div className="choice-grid">
                    {categoryOptions.map(([val, label], i) => (
                      <label className="choice" key={val}>
                        <input type="checkbox" name="categories" value={val} defaultChecked={i === 0} />
                        {label}
                      </label>
                    ))}
                  </div>
                </div>

                <div className="split-field">
                  <label>
                    Quantity Needed
                    <input required type="number" name="requested_quantity" min="1" step="1" defaultValue="30" />
                  </label>
                  <label>
                    Unit
                    <select name="unit" defaultValue="portions">
                      <option value="portions">portions</option>
                      <option value="items">items</option>
                      <option value="kg">kg</option>
                    </select>
                  </label>
                </div>

                <label>
                  Urgency Level (1–10)
                  <input required name="urgency" type="range" min="1" max="10" defaultValue="7" />
                </label>

                <div className="span-2">
                  <span className="input-heading">Storage Capabilities on Site</span>
                  <div className="choice-grid">
                    {storageOptions.map((v, i) => (
                      <label className="choice" key={v}>
                        <input type="checkbox" name="storage" value={v} defaultChecked={i < 2} />
                        {readable(v)}
                      </label>
                    ))}
                  </div>
                </div>

                <label className="span-2">
                  Delivery / Collection Address
                  <input required name="delivery_address" placeholder="Address where food is distributed" />
                </label>

                <label className="span-2">
                  Needed By
                  <input required name="needed_by" type="datetime-local" defaultValue={dateField(6)} />
                </label>

                <input type="hidden" name="latitude" value="12.9716" />
                <input type="hidden" name="longitude" value="77.5946" />
              </>
            )}
          </div>

          <div className="modal-footer">
            <button type="button" className="button ghost" onClick={onClose}>Cancel</button>
            <button type="submit" className="button primary">
              <Check size={16} /> {isDonation ? "Publish Live Listing" : "Submit Community Request"}
            </button>
          </div>
        </form>
      </section>
    </div>
  );
}

export default App;