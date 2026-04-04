@import hexlet.code.model.Url
@import hexlet.code.model.UrlCheck
@import hexlet.code.utils.ViewUtils
@import java.util.List

@param String flashType
@param String flashMessage
@param Url url
@param List<UrlCheck> checks

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
                            <td>${ViewUtils.formatDateTime(url.createdAt())}</td>
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
                        @for(UrlCheck check : checks)
                            <tr>
                                <td>${check.id()}</td>
                                <td>${check.statusCode()}</td>
                                <td>${ViewUtils.truncate(check.h1())}</td>
                                <td>${ViewUtils.truncate(check.title())}</td>
                                <td>${ViewUtils.truncate(check.description())}</td>
                                <td>${ViewUtils.formatDateTime(check.createdAt())}</td>
                            </tr>
                        @endfor
                    </tbody>
                </table>
            </div>
        </div>
    `
)
