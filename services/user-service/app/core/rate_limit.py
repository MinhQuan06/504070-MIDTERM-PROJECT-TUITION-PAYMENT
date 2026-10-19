from slowapi import Limiter
from slowapi.util import get_remote_address

# IP-based protection for repeated login requests. If deployed behind a reverse
# proxy, configure trusted proxy handling at the deployment layer; do not blindly
# trust arbitrary X-Forwarded-For headers from the public internet.
limiter = Limiter(key_func=get_remote_address)
