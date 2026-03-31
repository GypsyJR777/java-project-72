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
            </div>
        </div>
    `
)
