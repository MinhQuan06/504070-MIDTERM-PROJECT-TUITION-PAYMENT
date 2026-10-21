using Microsoft.EntityFrameworkCore;
using Student.Data;

var builder = WebApplication.CreateBuilder(args);

var connectionString =
    builder.Configuration.GetConnectionString("student_db")
    ?? throw new InvalidOperationException(
        "Connection string 'student_db' was not found.");

builder.Services.AddDbContext<StudentDbContext>(options =>
    options.UseMySQL(connectionString)
);

builder.Services.AddHttpClient("user-auth", client => {
    client.BaseAddress = new Uri(builder.Configuration["UserService:Url"] ?? "http://localhost:8001");
    client.Timeout = TimeSpan.FromSeconds(5);
});
builder.Services.AddScoped<Student.Security.AdminWriteFilter>();
builder.Services.AddControllers(options => options.Filters.AddService<Student.Security.AdminWriteFilter>());
builder.Services.AddEndpointsApiExplorer();
builder.Services.AddSwaggerGen();
var app = builder.Build();

if (app.Environment.IsDevelopment())
{
    app.UseSwagger();
    app.UseSwaggerUI();
}
app.UseHttpsRedirection();
app.UseAuthorization();
app.MapControllers();
app.MapGet("/", () => Results.Redirect("/swagger"));
app.Run();