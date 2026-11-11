// Small wrapper around fetch for the JSON API. The login cookie is HttpOnly,
// so the browser sends it for us and scripts never see the token.
async function api(method, path, body) {
    const response = await fetch(path, {
        method,
        headers: body === undefined ? {} : { "Content-Type": "application/json" },
        body: body === undefined ? undefined : JSON.stringify(body),
        credentials: "same-origin",
    });

    if (response.status === 401 && !path.startsWith("/api/auth/")) {
        window.location.href = "/login";
        throw new Error("Please log in again");
    }
    if (!response.ok) {
        const problem = await response.json().catch(() => ({}));
        throw new Error(problem.detail || problem.title || "Something went wrong");
    }
    return response.status === 204 ? null : response.json();
}

// Builds a DOM element. Text always goes through textContent, never innerHTML,
// so menu names typed by a restaurant cannot inject markup.
function el(tag, props = {}, ...children) {
    const node = document.createElement(tag);
    for (const [key, value] of Object.entries(props)) {
        if (key === "class") {
            node.className = value;
        } else if (key.startsWith("on")) {
            node.addEventListener(key.slice(2), value);
        } else if (value === true) {
            node.setAttribute(key, "");
        } else if (value !== false && value !== null && value !== undefined) {
            node.setAttribute(key, value);
        }
    }
    for (const child of children.flat()) {
        if (child !== null && child !== undefined && child !== false) {
            node.append(child);
        }
    }
    return node;
}
