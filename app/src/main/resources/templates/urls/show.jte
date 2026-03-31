@import hexlet.code.model.Url

@param String flashType
@param String flashMessage
@param Url url

@template.layout.main(
    title = "Сайт",
    flashType = flashType,
    flashMessage = flashMessage,
    content = @`
        <div class="row">
            <div class="col-12">
                <h1 class="mb-4">Сайт: ${url.name()}</h1>
                <table data-test="url" class="table table-bordered table-hover">
                    <tbody>
                        <tr>
                            <td>ID</td>
                            <td>${url.id()}</td>
                        </tr>
                        <tr>
                            <td>Имя</td>
                            <td>${url.name()}</td>
                        </tr>
                        <tr>
                            <td>Дата создания</td>
                            <td>${url.createdAt().toLocalDateTime().toLocalDate().toString()}</td>
                        </tr>
                    </tbody>
                </table>
                <form method="post" action="/urls/${url.id()}/checks" class="mb-4">
                    <input type="submit" value="Запустить проверку" class="btn btn-primary">
                </form>
                <table data-test="checks" class="table table-bordered table-hover">
                    <thead>
                        <tr>
                            <th>ID</th>
                            <th>Код ответа</th>
                            <th>h1</th>
                            <th>title</th>
                            <th>description</th>
                            <th>Дата создания</th>
                        </tr>
                    </thead>
                    <tbody>
                    </tbody>
                </table>
            </div>
        </div>
    `
)
