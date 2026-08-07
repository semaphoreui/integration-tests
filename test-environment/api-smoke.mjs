const baseUrl = process.env.SEMAPHORE_BASE_URL || "http://localhost:3000";
const username = process.env.SEMAPHORE_USERNAME || "admin";
const password = process.env.SEMAPHORE_PASSWORD || "test-password";

let cookie = "";
let projectId = null;

function check(condition, message) {
  if (!condition) throw new Error(message);
}

async function request(path, options = {}) {
  const headers = new Headers(options.headers || {});
  if (options.body !== undefined) headers.set("content-type", "application/json");
  if (cookie) headers.set("cookie", cookie);

  return fetch(`${baseUrl}${path}`, {
    ...options,
    headers,
    body: options.body === undefined ? undefined : JSON.stringify(options.body),
    redirect: "manual",
  });
}

async function expectStatus(label, response, expected) {
  check(
    response.status === expected,
    `${label}: expected HTTP ${expected}, received ${response.status}`,
  );
  console.log(`PASS ${label} (${response.status})`);
}

try {
  const ping = await request("/api/ping");
  await expectStatus("health", ping, 200);
  check((await ping.text()) === "pong", "health: expected body 'pong'");

  const invalidLogin = await request("/api/auth/login", {
    method: "POST",
    body: { auth: username, password: `${password}-invalid` },
  });
  check(invalidLogin.status >= 400, `invalid login: expected 4xx, received ${invalidLogin.status}`);
  console.log(`PASS invalid login (${invalidLogin.status})`);

  const login = await request("/api/auth/login", {
    method: "POST",
    body: { auth: username, password },
  });
  await expectStatus("valid login", login, 204);
  cookie = login.headers.getSetCookie().map((value) => value.split(";", 1)[0]).join("; ");
  check(cookie.length > 0, "valid login: session cookie was not returned");

  const uniqueName = `api-smoke-${Date.now()}`;
  const createProject = await request("/api/projects", {
    method: "POST",
    body: { name: uniqueName, alert: false, max_parallel_tasks: 0 },
  });
  await expectStatus("create project", createProject, 201);
  const project = await createProject.json();
  projectId = project.id;
  check(Number.isInteger(projectId), "create project: integer id was not returned");
  check(project.name === uniqueName, "create project: response name does not match request");

  const getProject = await request(`/api/project/${projectId}`);
  await expectStatus("get project", getProject, 200);
  const savedProject = await getProject.json();
  check(savedProject.id === projectId, "get project: response id does not match created project");

  const role = await request(`/api/project/${projectId}/role`);
  await expectStatus("get project role", role, 200);
  const roleBody = await role.json();
  check(roleBody.role === "owner", `get project role: expected owner, received ${roleBody.role}`);
} finally {
  if (projectId !== null && cookie) {
    const cleanup = await request(`/api/project/${projectId}`, { method: "DELETE" });
    await expectStatus("cleanup project", cleanup, 204);
  }
}

console.log("API smoke completed successfully");
