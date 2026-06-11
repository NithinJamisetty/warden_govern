// SWMS Common Authentication Utility

const TOKEN_KEY = "swms_auth_token";

function getToken() {
    return sessionStorage.getItem(TOKEN_KEY);
}

function setToken(token) {
    sessionStorage.setItem(TOKEN_KEY, token);
}

function removeToken() {
    sessionStorage.removeItem(TOKEN_KEY);
}

function parseJwt(token) {
    try {
        const base64Url = token.split('.')[1];
        const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/');
        const jsonPayload = decodeURIComponent(window.atob(base64).split('').map(function(c) {
            return '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2);
        }).join(''));

        const payload = JSON.parse(jsonPayload);
        if (payload && payload.hostel && !payload.hostelName) {
            payload.hostelName = payload.hostel;
        }
        return payload;
    } catch (e) {
        return null;
    }
}

function getUser() {
    const token = getToken();
    if (!token) return null;
    return parseJwt(token);
}

function checkAuth(allowedRoles = []) {
    const user = getUser();
    const isAdminPath = window.location.pathname.includes('/admin/');

    if (!user) {
        // Redirect to appropriate login page
        window.location.href = isAdminPath ? "/admin/login.html" : "/warden/login.html";
        return null;
    }

    if (allowedRoles.length > 0 && !allowedRoles.includes(user.role)) {
        alert("Access Denied: You do not have permission to view this page.");
        window.location.href = isAdminPath ? "/admin/login.html" : "/warden/login.html";
        return null;
    }

    // Set page welcome username if element exists
    document.addEventListener("DOMContentLoaded", () => {
        const usernameEl = document.getElementById("nav-username");
        if (usernameEl) {
            usernameEl.textContent = user.username;
        }
        const userRoleEl = document.getElementById("nav-user-role");
        if (userRoleEl) {
            userRoleEl.textContent = formatRole(user.role);
            if (user.role === "DISTRICT_ADMIN") {
                userRoleEl.className = "badge bg-info text-white px-3 py-2";
            } else if (user.role === "SUPER_ADMIN") {
                userRoleEl.className = "badge bg-danger text-white px-3 py-2";
            }
        }
        const userHostelEl = document.getElementById("nav-user-hostel");
        if (userHostelEl && user.hostelName) {
            userHostelEl.textContent = user.hostelName;
        }
    });

    return user;
}

function formatRole(role) {
    if (role === "SUPER_ADMIN") return "Super Administrator";
    if (role === "DISTRICT_ADMIN") return "District Administrator";
    if (role === "WARDEN") return "Hostel Warden";
    return role;
}

async function fetchApi(url, options = {}) {
    const token = getToken();
    const headers = options.headers || {};

    if (token) {
        headers["Authorization"] = "Bearer " + token;
    }
    
    // Default content type to JSON if sending body and not specified
    if (options.body && !headers["Content-Type"]) {
        headers["Content-Type"] = "application/json";
    }

    options.headers = headers;

    const response = await fetch(url, options);

    if (response.status === 401 || response.status === 403) {
        removeToken();
        const isAdminPath = window.location.pathname.includes('/admin/');
        window.location.href = isAdminPath ? "/admin/login.html" : "/warden/login.html";
        throw new Error("Session expired or access denied.");
    }

    return response;
}

function logout() {
    const user = getUser();
    const isAdmin = user && (user.role === "SUPER_ADMIN" || user.role === "DISTRICT_ADMIN");
    removeToken();
    window.location.href = isAdmin ? "/admin/login.html" : "/index.html";
}

// Inactivity Auto-Logout (5 Minutes Limit)
let inactivityTimeout;
const INACTIVITY_LIMIT = 5 * 60 * 1000; // 5 minutes

function resetInactivityTimer() {
    clearTimeout(inactivityTimeout);
    inactivityTimeout = setTimeout(() => {
        alert("Session expired due to inactivity. Logging out...");
        logout();
    }, INACTIVITY_LIMIT);
}

// Start tracking inactivity on protected pages (where token is present)
if (getToken()) {
    resetInactivityTimer();
    const activityEvents = ['mousemove', 'keypress', 'click', 'scroll', 'touchstart'];
    activityEvents.forEach(event => {
        window.addEventListener(event, resetInactivityTimer, { passive: true });
    });
}
