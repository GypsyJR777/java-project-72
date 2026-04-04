@import hexlet.code.model.Url
@import hexlet.code.model.UrlCheck
@import hexlet.code.utils.ViewUtils
@import java.util.List
@import java.util.Map

@param String flashType
@param String flashMessage
@param List<Url> urls
@param Map<Long, UrlCheck> latestChecks

@template.layout.main(
    title = "Сайты",
    flashType = flashType,
    flashMessage = flashMessage,
    content = @`
        <div class="row">
            <div class="col-12">
                <h1 class="mb-4">Сайты</h1>
                <table data-test="urls" class="table table-bordered table-hover">
                    <thead>
                        <tr>
                            <th>ID</th>
                            <th>Имя</th>
                            <th>Дата создания</th>
                            <th>Код ответа</th>
                        </tr>
                    </thead>
                    <tbody>
                        @for(Url url : urls)
                            <tr>
                                <td>${url.id()}</td>
                                <td><a href="/urls/${url.id()}">${url.name()}</a></td>
                                <td>
                                    ${latestChecks.get(url.id()) == null
                                        ? ""
                                        : ViewUtils.formatDateTime(latestChecks.get(url.id()).createdAt())}
                                </td>
                                <td>
                                    ${latestChecks.get(url.id()) == null
                                        ? ""
                                        : latestChecks.get(url.id()).statusCode().toString()}
                                </td>
                            </tr>
                        @endfor
                    </tbody>
                </table>
            </div>
        </div>
    `
)
