using Microsoft.AspNetCore.Mvc;
using Microsoft.AspNetCore.Mvc.Filters;
using System.Net;
using System.Text.Json;

namespace Student.Security;

// Direct-port access must enforce the same permissions as access through Gateway.
public sealed class AdminWriteFilter(IHttpClientFactory clients) : IAsyncActionFilter
{
    public async Task OnActionExecutionAsync(ActionExecutingContext context, ActionExecutionDelegate next)
    {
        var request = context.HttpContext.Request;
        if (HttpMethods.IsGet(request.Method) || HttpMethods.IsHead(request.Method)
            || context.ActionDescriptor.RouteValues["action"] == "PayTuition")
        {
            await next();
            return;
        }
        var authorization = request.Headers.Authorization.ToString();
        if (!authorization.StartsWith("Bearer ", StringComparison.OrdinalIgnoreCase)
            || string.IsNullOrWhiteSpace(authorization[7..]))
        {
            context.Result = new UnauthorizedObjectResult(new { message = "Bearer authentication is required." });
            return;
        }
        try
        {
            using var message = new HttpRequestMessage(HttpMethod.Get, "/api/v1/users/me");
            message.Headers.TryAddWithoutValidation("Authorization", authorization);
            using var response = await clients.CreateClient("user-auth").SendAsync(message, context.HttpContext.RequestAborted);
            if (response.StatusCode == HttpStatusCode.Unauthorized)
            {
                context.Result = new UnauthorizedObjectResult(new { message = "Invalid or expired credentials." });
                return;
            }
            if (!response.IsSuccessStatusCode) throw new HttpRequestException("User authentication unavailable");
            using var profile = JsonDocument.Parse(await response.Content.ReadAsStringAsync());
            if (!profile.RootElement.TryGetProperty("role", out var role)
                || !string.Equals(role.GetString(), "admin", StringComparison.OrdinalIgnoreCase))
            {
                context.Result = new ObjectResult(new { message = "Admin role required." }) { StatusCode = 403 };
                return;
            }
        }
        catch (Exception error) when (error is HttpRequestException or TaskCanceledException or JsonException)
        {
            context.Result = new ObjectResult(new { message = "Authentication service unavailable." }) { StatusCode = 503 };
            return;
        }
        await next();
    }
}
