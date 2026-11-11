// Shared by the login and register pages. The form's data-endpoint says which
// API call to make, and its field names match the request body.
const form = document.querySelector("form[data-endpoint]");
const error = document.querySelector(".error");

form.addEventListener("submit", async (event) => {
    event.preventDefault();
    error.textContent = "";
    const body = Object.fromEntries(new FormData(form));
    try {
        await api("POST", form.dataset.endpoint, body);
        window.location.href = "/admin/menu";
    } catch (e) {
        error.textContent = e.message;
    }
});
