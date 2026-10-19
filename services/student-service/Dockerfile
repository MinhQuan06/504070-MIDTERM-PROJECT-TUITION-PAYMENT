# =======================================================
# Giai đoạn 1: Build mã nguồn bằng .NET 10 SDK
# =======================================================
FROM mcr.microsoft.com/dotnet/sdk:10.0 AS build
WORKDIR /src

# Copy file định nghĩa project và tải thư viện NuGet
COPY ["Student.csproj", "./"]
RUN dotnet restore "Student.csproj"

# Copy toàn bộ mã nguồn và biên dịch bản Release
COPY . .
RUN dotnet publish "Student.csproj" -c Release -o /app/publish /p:UseAppHost=false

# =======================================================
# Giai đoạn 2: Chạy ứng dụng bằng .NET 10 Runtime siêu nhẹ
# =======================================================
FROM mcr.microsoft.com/dotnet/aspnet:10.0 AS final
WORKDIR /app
COPY --from=build /app/publish .

# Mở cổng 5071 và bật chế độ Development để luôn có Swagger UI
EXPOSE 5071
ENV ASPNETCORE_URLS=http://+:5071
ENV ASPNETCORE_ENVIRONMENT=Development

ENTRYPOINT ["dotnet", "Student.dll"]
