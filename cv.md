# [YOUR NAME]

**Email:** your.email@example.com | **GitHub:** github.com/yourusername | **LinkedIn:** linkedin.com/in/yourprofile | **Medium:** @yourmedium

---

## EDUCATION

**[Your University Name]**  
Bachelor of Science in Computer Science (or relevant major) | Expected Graduation: [Month Year]  
GPA: [Your GPA] | Relevant Coursework: Data Structures, Algorithms, Operating Systems

---

## TECHNICAL SKILLS

**Languages:** Java, JavaScript, Python, SQL  
**Frameworks & Libraries:** Spring Boot, Spring Security, Redis, RabbitMQ, PostgreSQL, React (if applicable)  
**Concurrency & Systems:** Virtual Threads, Memory Models (JMM), Perceptual Hashing, Distributed Locks, Server-Sent Events  
**Tools & Platforms:** Docker, Git, Azure, Render, Maven/Gradle, OAuth2, JWT

---

## PROJECTS

### **SentinelLock** | [GitHub Link]
*Single Node Lock Service with Fencing Tokens*

- Built time-based lease system with fencing tokens using Spring Boot and Redis to prevent stale writes in distributed environments
- Implemented dual expiry handling (Redis keyspace events + active polling) for reliable lease management with configurable eviction
- Used gRPC for high-performance API and Java 21 virtual threads for concurrent request handling with bounded event queues
- Designed request queuing with exponential backoff retry logic to handle resource contention and race conditions

### **Streamline** | [GitHub Link] | [JitPack](https://jitpack.io/#kusoroadeolu/streamline-spring-boot-starter)
*Spring Boot SSE Management Library*

- Built thread-safe SSE management library using Java 21 virtual threads for non-blocking concurrent I/O at scale
- Designed registry pattern with bounded event history, configurable eviction policies (FIFO/LIFO/STRICT), and automatic cleanup
- Implemented event replay system for reconnecting clients with flexible filtering options (all, range, custom predicates)
- Created immutable emitter wrapper preventing callback overwrites across service boundaries; published to JitPack

### **RevGif** | [GitHub Link] | [Live Demo](https://revgif.onrender.com)
*Reverse GIF Search Engine*

- Built perceptual hash-based reverse image search using DCT hash algorithm with Gaussian blur preprocessing for noise reduction
- Implemented asymmetric frame extraction strategy (4 frames for uploads, all for downloads) to optimize API costs vs accuracy
- Designed database-first caching with PostgreSQL bitwise operations (XOR + BIT_COUNT) achieving <500ms cached query response
- Integrated Google Gemini AI with structured prompt engineering for automatic search query generation; uses SSE for real-time streaming

### **VibeMatch** | [GitHub Link] | [Live Demo](https://vibematch-od18.onrender.com)
*Music Compatibility Social Platform*

- Built social platform connecting users through Spotify listening habits using cosine similarity and Jaccard index for compatibility scoring
- Implemented async data pipeline with RabbitMQ, handling rate limits via Dead Letter Exchange and exponential backoff retry patterns
- Designed multi-layer caching strategy (Redis) for OAuth tokens (1hr), user profiles (24hr), and task status with cache-aside pattern
- Created OAuth2 flow with automatic token refresh, pessimistic locking for concurrent syncs, and scheduled auto-sync cron jobs

### **EventDrop** | [GitHub Link] | [Live Demo](https://eventdrop1-bxgbf8btf6aqd3ha.francecentral-01.azurewebsites.net/)
*Ephemeral File Sharing Platform*

- Built full-stack ephemeral file sharing app with Spring Boot, Redis, RabbitMQ, and Azure Blob Storage with PWA support
- Designed multi-layered cleanup system (Redis TTL, event-driven cascades, scheduled jobs, storage lifecycle policies) to prevent data leaks
- Implemented hybrid event architecture: RabbitMQ for durable async tasks, Spring ApplicationEventPublisher for real-time SSE updates
- Solved race condition in concurrent batch uploads with atomic validation; deployed to Azure with quota enforcement (30 files, 2GB per room)

### **Clique** | [GitHub Link] | 
*Multi module Terminal Styling Library for Java*

- Created dependency-free Java library for beautifying CLI applications with clean markup syntax (`[red, bold]text[/]`)
- Implemented markup parser with configurable delimiters, auto-closing tags, and strict validation modes for error handling
- Built theming system supporting popular color schemes (Catppuccin, Dracula, Nord, Gruvbox) with separate themes module
- Designed components for tables, progress bars, boxes, and text formatting with consistent API; published to Maven central and JitPack

---

## RESEARCH & EXPERIMENTATION

### **vic-utils - Experimental Concurrency Primitives** | [GitHub Link]
*Deep dive into concurrency theory through from-scratch implementations*

- Implemented CSP channels from scratch (lock-based, lock-free, buffered) with performance benchmarking showing 50x throughput difference (5M vs 100K ops/s) and variance trade-off analysis (12% vs 2.5% coefficient of variation under contention)
- Designed somewhat novel **Optimistic Entity pattern** combining version-based optimistic locking, single-threaded actor processing, and event sourcing with all-or-nothing batch proposals
- Discovered and documented reference equality deadlock bug caused by Java string interning in rendezvous semantics; solved with Box wrapper pattern ensuring unique object references
- Built actor model with supervision trees using virtual threads, custom mutex with CAS-based fairness, and channel-backed semaphore implementing CSP semantics

---

## TECHNICAL WRITING

**Concurrency & Performance Blog Posts** | Medium: @yourmedium

- **"Implementing Go's Channels in Java"** - Explored Communicating Sequential Processes (CSP) by porting Go-style channels to Java with virtual threads, addressing TOCTOU bugs and thread safety in concurrent systems
- **"Memory Barriers and Happens-Before Guarantees"** - Deep dive into the Java Memory Model (JMM), explaining hardware-level memory barriers, volatile semantics, and happens-before rules for preventing data races
- **"Optimizing Virtual Thread Overhead"** - Performance benchmarking comparing virtual threads to platform threads in high-frequency coordination tasks, discovering rendezvous overhead and mitigation strategies

---

## ADDITIONAL INFORMATION

**Interests:** Distributed Systems, Concurrency Patterns, Algorithm Design, Developer Tooling  
**Open Source:** Published multiple libraries to Maven and JitPack; active contributor to personal projects with comprehensive documentation