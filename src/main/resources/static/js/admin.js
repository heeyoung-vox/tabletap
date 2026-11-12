// Shared by every admin page: shows who is logged in and wires up logout.
api("GET", "/api/admin/me").then((me) => {
    document.getElementById("who").textContent = me.email;
});

document.getElementById("logout").addEventListener("click", async () => {
    await api("POST", "/api/auth/logout");
    window.location.href = "/login";
});
