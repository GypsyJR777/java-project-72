@import gg.jte.Content

@param String title
@param String flashType
@param String flashMessage
@param Content content

<!doctype html>
<html lang="ru">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>${title}</title>
    <link
        href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.7/dist/css/bootstrap.min.css"
        rel="stylesheet"
        integrity="sha384-LN+7fdVzj6u52u30Kp6M/trliBMCMKTyK833zpbD+pXdCLuTusPj697FH4R/5mcr"
        crossorigin="anonymous"
    >
</head>
<body>
<nav class="navbar navbar-expand-lg navbar-light bg-white border-bottom mb-4">
    <div class="container">
        <a class="navbar-brand" href="/">Анализатор страниц</a>
    </div>
</nav>
<main class="container-lg">
    @if(flashMessage != null)
        <div class="alert alert-${flashType == null ? "info" : flashType}" role="alert">
            ${flashMessage}
        </div>
    @endif
    ${content}
</main>
</body>
</html>
