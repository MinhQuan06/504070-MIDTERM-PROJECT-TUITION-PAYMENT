using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Student.Data;

namespace Student.Controllers
{
    [ApiController]
    [Route("api/[controller]")]
    public class DatabaseController : ControllerBase
    {
        private readonly StudentDbContext _context;
        private readonly ILogger<DatabaseController> _logger;

        public DatabaseController(StudentDbContext context, ILogger<DatabaseController> logger)
        {
            _context = context;
            _logger = logger;
        }

        /// <summary>
        /// Kiểm tra trạng thái kết nối tới cơ sở dữ liệu MySQL
        /// </summary>
        [HttpGet("test")]
        public async Task<IActionResult> TestConnection()
        {
            try
            {
                bool connected = await _context.Database.CanConnectAsync();

                if (connected)
                {
                    return Ok(new
                    {
                        status = "Success",
                        message = "Connected to MySQL successfully."
                    });
                }

                return StatusCode(500, new
                {
                    status = "Error",
                    message = "Cannot connect to MySQL database."
                });
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Database connection test failed.");

                return StatusCode(500, new
                {
                    status = "Error",
                    message = "Database connection failed. Please check server logs and configuration."
                });
            }
        }
    }
}